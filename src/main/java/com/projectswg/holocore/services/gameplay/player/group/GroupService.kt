package com.projectswg.holocore.services.gameplay.player.group

import com.projectswg.common.data.encodables.chat.ChatAvatar
import com.projectswg.common.data.encodables.oob.ProsePackage
import com.projectswg.common.data.encodables.oob.StringId
import com.projectswg.common.data.sui.SuiEvent
import com.projectswg.holocore.intents.gameplay.player.group.GroupEventIntent
import com.projectswg.holocore.intents.gameplay.player.group.GroupEventIntent.GroupEventType
import com.projectswg.holocore.intents.support.global.chat.ChatRoomUpdateIntent
import com.projectswg.holocore.intents.support.global.chat.ChatRoomUpdateIntent.UpdateType
import com.projectswg.holocore.intents.support.global.chat.SystemMessageIntent
import com.projectswg.holocore.intents.support.global.zone.PlayerEventIntent
import com.projectswg.holocore.intents.support.objects.swg.DestroyObjectIntent
import com.projectswg.holocore.intents.support.objects.swg.ObjectCreatedIntent
import com.projectswg.holocore.resources.support.global.player.Player
import com.projectswg.holocore.resources.support.global.player.Player.PlayerServer
import com.projectswg.holocore.resources.support.global.player.PlayerEvent
import com.projectswg.holocore.resources.support.global.zone.sui.SuiButtons
import com.projectswg.holocore.resources.support.global.zone.sui.SuiListBox
import com.projectswg.holocore.resources.support.objects.ObjectCreator
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject
import com.projectswg.holocore.resources.support.objects.swg.group.GroupObject
import com.projectswg.holocore.services.support.objects.ObjectStorageService.ObjectLookup
import me.joshlarson.jlcommon.control.IntentHandler
import me.joshlarson.jlcommon.control.Service
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class GroupService(private val groups: MutableMap<Long, GroupObject> = ConcurrentHashMap()) : Service() {

	@IntentHandler
	private fun handleGroupEventIntent(gei: GroupEventIntent) {
		when (gei.eventType) {
			GroupEventType.INVITE -> handleGroupInvite(gei.player, gei.target)
			GroupEventType.UNINVITE -> handleGroupUninvite(gei.player, gei.target)
			GroupEventType.JOIN -> handleGroupJoin(gei.player)
			GroupEventType.DECLINE -> handleGroupDecline(gei.player)
			GroupEventType.DISBAND -> handleGroupDisband(gei.player)
			GroupEventType.LEAVE -> handleGroupLeave(gei.player)
			GroupEventType.MAKE_LEADER -> handleMakeLeader(gei.player, gei.target)
			GroupEventType.KICK -> handleKick(gei.player, gei.target)
			GroupEventType.MAKE_MASTER_LOOTER -> handleMakeMasterLooter(gei.player, gei.target)
			GroupEventType.LOOT -> handleGroupLootOptions(gei.player)
		}
	}

	@IntentHandler
	private fun handlePlayerEventIntent(pei: PlayerEventIntent) {
		when (pei.event) {
			PlayerEvent.PE_ZONE_IN_SERVER -> handleMemberRezoned(pei.player)
			PlayerEvent.PE_DISAPPEAR -> handleMemberDisappeared(pei.player)
			else -> {}
		}
	}

	private fun handleGroupLootOptions(groupLeader: Player) {
		val window = SuiListBox(SuiButtons.OK_CANCEL, "@group:set_loot_type_title", "@group:set_loot_type_text")
		window.addListItem("Free For All")
		window.addListItem("Master Looter")
		window.addListItem("Lottery")
		window.addListItem("Random")
		window.addCallback("handleSelectedItem") { event: SuiEvent, parameters: Map<String?, String?>? ->
			if (event == SuiEvent.OK_PRESSED) {
				val selectedRow = SuiListBox.getSelectedRow(parameters)
				val lootRuleMsg: String = when (window.getListItem(selectedRow).name) {
					"Free For All" -> "selected_free4all"
					"Master Looter" -> "selected_master"
					"Lottery" -> "selected_lotto"
					"Random" -> "selected_random"
					else -> "selected_free4all"
				}

				val groupObject = ObjectLookup.getObjectById(groupLeader.creatureObject.groupId) as GroupObject?

				if (groupObject != null) {
					groupObject.setLootRule(selectedRow)
					groupObject.displayLootRuleChangeBox(lootRuleMsg)
					sendSystemMessage(groupLeader, lootRuleMsg)
				}
			}
		}

		window.display(groupLeader)
	}

	private fun handleMemberRezoned(player: Player) {
		val creatureObject = player.creatureObject
		if (creatureObject.groupId == 0L) return
		val groupObject = getGroup(creatureObject.groupId)
		if (groupObject == null) {
			// Group was destroyed while logged out
			creatureObject.groupId = 0
		}
	}

	private fun handleMemberDisappeared(player: Player) {
		val creature = player.creatureObject
		Objects.requireNonNull(creature)
		if (creature.groupId == 0L) return  // Ignore anyone without a group
		removePlayerFromGroup(creature)
	}

	private fun handleGroupDisband(player: Player) {
		val creo = player.creatureObject
		Objects.requireNonNull(creo)
		val group = getGroup(creo.groupId)
		Objects.requireNonNull(group)
		if (group!!.leaderId != creo.objectId) {
			sendSystemMessage(player, "must_be_leader")
			return
		}
		destroyGroup(group, player)
	}

	private fun handleGroupLeave(player: Player) {
		removePlayerFromGroup(player.creatureObject)
	}

	private fun handleGroupInvite(player: Player, target: CreatureObject?) {
		val creature = player.creatureObject
		Objects.requireNonNull(creature)

		if (target == null || !target.isPlayer || creature == target) {
			sendSystemMessage(player, "invite_no_target_self")
			return
		}
		if (target.groupId != 0L) {
			sendSystemMessage(player, "already_grouped", "TT", target.objectId)
			return
		}
		val groupId = creature.groupId
		if (groupId != 0L) {
			val group = getGroup(groupId)
			Objects.requireNonNull(group)
			if (!handleInviteToExistingGroup(player, target, group)) return
		}
		sendInvite(player, target, groupId)
	}

	private fun handleInviteToExistingGroup(inviter: Player, target: CreatureObject, group: GroupObject?): Boolean {
		if (group!!.leaderId != inviter.creatureObject.objectId) {
			sendSystemMessage(inviter, "must_be_leader")
			return false
		}
		if (group.isFull) {
			sendSystemMessage(inviter, "full")
			return false
		}
		if (target.inviterData.groupId != 0L) {
			if (target.inviterData.groupId != inviter.creatureObject.groupId) {
				sendSystemMessage(inviter, "considering_other_group", "TT", target.objectId)
			} else {
				sendSystemMessage(inviter, "considering_your_group", "TT", target.objectId)
			}

			return false
		}
		return true
	}

	private fun handleGroupUninvite(player: Player, target: CreatureObject?) {
		if (target == null) {
			sendSystemMessage(player, "uninvite_no_target_self")
			return
		}
		val targetOwner = target.owner
		Objects.requireNonNull(targetOwner)
		val targetName = targetOwner!!.characterName
		if (target.inviterData == null) {
			sendSystemMessage(player, "uninvite_not_invited", "TT", targetName)
			return
		}
		sendSystemMessage(player, "uninvite_self", "TT", targetName)
		sendSystemMessage(targetOwner, "uninvite_target", "TT", player.characterName)
		clearInviteData(target)
	}

	private fun handleGroupJoin(player: Player) {
		val creature = player.creatureObject
		try {
			val sender = creature.inviterData.sender
			if (sender == null || sender.playerServer != PlayerServer.ZONE) { // Inviter logged out, invitation is no good
				sendSystemMessage(player, "must_be_invited")
				return
			}

			val groupId = creature.inviterData.groupId
			val groupAlreadyExists = groups.containsKey(groupId)

			if (groupAlreadyExists) {
				joinGroup(sender.creatureObject, creature, groupId)
			} else {
				createGroup(sender, player)
			}
		} finally {
			clearInviteData(creature)
		}
	}

	private fun handleGroupDecline(invitee: Player) {
		val creature = invitee.creatureObject
		val invitation = creature.inviterData
		sendSystemMessage(invitee, "decline_self", "TT", invitation.sender!!.characterName)
		sendSystemMessage(invitation.sender, "decline_leader", "TT", invitee.characterName)
		clearInviteData(creature)
	}

	private fun handleMakeLeader(currentLeader: Player, newLeader: CreatureObject?) {
		val currentLeaderCreature = currentLeader.creatureObject
		Objects.requireNonNull(currentLeaderCreature)
		assert(newLeader!!.groupId != 0L) { "new leader is not a part of a group" }
		val group = getGroup(newLeader.groupId)
		Objects.requireNonNull(group)
		if (group!!.leaderId != currentLeaderCreature.objectId) {
			sendSystemMessage(currentLeader, "must_be_leader")
			return
		}
		if (group.leaderId == newLeader.objectId) return

		// Set the group leader to newLeader
		sendGroupSystemMessage(group, "new_leader", "TU", newLeader.objectName)
		group.setLeader(newLeader)
	}

	private fun handleMakeMasterLooter(player: Player, target: CreatureObject?) {
		val creature = player.creatureObject
		if (creature.groupId == 0L) {
			sendSystemMessage(player, "group_only")
			return
		}
		if (target!!.owner == null) {
			return
		}
		val group = getGroup(creature.groupId)
		Objects.requireNonNull(group)
		group!!.lootMaster = target.objectId
		sendGroupSystemMessage(group, "new_master_looter", "TU", target.objectName)
	}

	private fun handleKick(leader: Player, kickedCreature: CreatureObject?) {
		Objects.requireNonNull(kickedCreature)
		val leaderCreature = leader.creatureObject
		Objects.requireNonNull(leaderCreature)
		val groupId = leaderCreature.groupId
		if (groupId == 0L) { // Requester is not in a group
			sendSystemMessage(leader, "group_only")
			return
		}
		if (kickedCreature!!.groupId != groupId) { // Requester and Kicked are not in same group
			sendSystemMessage(leader, "must_be_leader")
			return
		}
		val group = getGroup(groupId)
		Objects.requireNonNull(group)
		if (group!!.leaderId != leaderCreature.objectId) { // Requester is not leader of group
			sendSystemMessage(leader, "must_be_leader")
			return
		}
		removePlayerFromGroup(kickedCreature)
	}

	private fun createGroup(leader: Player, member: Player) {
		val group = ObjectCreator.createObjectFromTemplate("object/group/shared_group_object.iff") as GroupObject
		Objects.requireNonNull(group)
		groups[group.objectId] = group
		group.formGroup(leader.creatureObject, member.creatureObject)
		ObjectCreatedIntent(group).broadcast()
		ChatRoomUpdateIntent(leader, ChatAvatar(leader.characterChatName), group.chatRoomPath, group.objectId.toString(), false).broadcast()
		sendSystemMessage(leader, "formed_self", "TT", leader.creatureObject.objectId)
		onJoinGroup(member.creatureObject, group)
	}

	private fun destroyGroup(group: GroupObject?, player: Player) {
		ChatRoomUpdateIntent(group!!.chatRoomPath, group.objectId.toString(), null, ChatAvatar(player.characterChatName), null, UpdateType.DESTROY).broadcast()
		sendGroupSystemMessage(group, "disbanded")
		group.disbandGroup()
		DestroyObjectIntent(group).broadcast()
		Objects.requireNonNull(groups.remove(group.objectId))
	}

	private fun joinGroup(inviter: CreatureObject, creature: CreatureObject, groupId: Long) {
		val player = creature.owner
		Objects.requireNonNull(player)
		val group = getGroup(groupId)
		Objects.requireNonNull(group)
		if (group!!.leaderId != inviter.objectId) {
			sendSystemMessage(player, "join_inviter_not_leader", "TT", inviter.objectName)
			return
		}
		if (group.isFull) {
			sendSystemMessage(player, "join_full")
			return
		}
		group.addMember(creature)
		onJoinGroup(creature, group)
	}

	private fun onJoinGroup(creature: CreatureObject, group: GroupObject?) {
		val player = creature.owner
		Objects.requireNonNull(player)
		sendSystemMessage(player, "joined_self")
		updateChatRoom(player, group, UpdateType.JOIN)
	}

	private fun removePlayerFromGroup(creature: CreatureObject?) {
		Objects.requireNonNull(creature)
		assert(creature!!.groupId != 0L) { "creature is not within a group" }
		val group = getGroup(creature.groupId)
		Objects.requireNonNull(group)

		// Check size of the group, if it only has two members, destroy the group
		if (group!!.groupMembers.size <= 2) {
			destroyGroup(group, group.leaderPlayer)
			return
		}
		sendSystemMessage(creature.owner, "removed")
		group.removeMember(creature)
		updateChatRoom(creature.owner, group, UpdateType.LEAVE)

		// If the leader has left, promote another group member to leader and notify the group of this
		if (creature.objectId == group.leaderId) {
			val newLeader = group.groupMemberObjects.iterator().next() // Pick a new leader
			group.setLeader(newLeader)
			sendGroupSystemMessage(group, "new_leader", "TU", newLeader.objectName)
		}
	}

	private fun updateChatRoom(player: Player?, group: GroupObject?, updateType: UpdateType) {
		ChatRoomUpdateIntent(player, group!!.chatRoomPath, updateType).broadcast()
	}

	private fun clearInviteData(creature: CreatureObject) {
		creature.updateGroupInviteData(null, 0)
	}

	private fun sendInvite(groupLeader: Player, invitee: CreatureObject, groupId: Long) {
		sendSystemMessage(invitee.owner, "invite_target", "TT", groupLeader.characterName)
		sendSystemMessage(groupLeader, "invite_leader", "TT", invitee.objectName)
		invitee.updateGroupInviteData(groupLeader, groupId)
	}

	private fun sendGroupSystemMessage(group: GroupObject?, id: String) {
		val members = group!!.groupMemberObjects
		for (member in members) {
			Objects.requireNonNull(member.owner)
			sendSystemMessage(member.owner, id)
		}
	}

	private fun sendGroupSystemMessage(group: GroupObject?, id: String, vararg objects: Any) {
		val members = group!!.groupMemberObjects
		for (member in members) {
			Objects.requireNonNull(member.owner)
			sendSystemMessage(member.owner, id, *objects)
		}
	}

	private fun getGroup(groupId: Long): GroupObject? {
		return groups[groupId]
	}

	private fun sendSystemMessage(target: Player?, id: String) {
		SystemMessageIntent(target!!, ProsePackage("group", id)).broadcast()
	}

	private fun sendSystemMessage(target: Player?, id: String, vararg objects: Any) {
		SystemMessageIntent.broadcastPersonal(target!!, ProsePackage(StringId("@group:$id"), *objects))
	}
}