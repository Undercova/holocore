/***********************************************************************************
 * Copyright (c) 2024 /// Project SWG /// www.projectswg.com                       *
 *                                                                                 *
 * ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on          *
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies. *
 * Our goal is to create an emulator which will provide a server for players to    *
 * continue playing a game similar to the one they used to play. We are basing     *
 * it on the final publish of the game prior to end-game events.                   *
 *                                                                                 *
 * This file is part of Holocore.                                                  *
 *                                                                                 *
 * --------------------------------------------------------------------------------*
 *                                                                                 *
 * Holocore is free software: you can redistribute it and/or modify                *
 * it under the terms of the GNU Affero General Public License as                  *
 * published by the Free Software Foundation, either version 3 of the              *
 * License, or (at your option) any later version.                                 *
 *                                                                                 *
 * Holocore is distributed in the hope that it will be useful,                     *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                  *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                   *
 * GNU Affero General Public License for more details.                             *
 *                                                                                 *
 * You should have received a copy of the GNU Affero General Public License        *
 * along with Holocore.  If not, see <http://www.gnu.org/licenses/>.               *
 ***********************************************************************************/
package com.projectswg.holocore.services.gameplay.combat.buffs

import com.projectswg.common.data.CRC
import com.projectswg.common.network.packets.swg.zone.PlayClientEffectObjectMessage
import com.projectswg.holocore.ProjectSWG
import com.projectswg.holocore.intents.gameplay.combat.CreatureKilledIntent
import com.projectswg.holocore.intents.gameplay.combat.buffs.BuffIntent
import com.projectswg.holocore.intents.gameplay.player.experience.skills.SkillModIntent
import com.projectswg.holocore.intents.support.global.zone.PlayerEventIntent
import com.projectswg.holocore.resources.support.data.server_info.StandardLog
import com.projectswg.holocore.resources.support.data.server_info.loader.BuffLoader.BuffInfo
import com.projectswg.holocore.resources.support.data.server_info.loader.DataLoader.Companion.commands
import com.projectswg.holocore.resources.support.data.server_info.loader.ServerData
import com.projectswg.holocore.resources.support.global.player.PlayerEvent
import com.projectswg.holocore.resources.support.objects.swg.creature.Buff
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject
import me.joshlarson.jlcommon.concurrency.ScheduledThreadPool
import me.joshlarson.jlcommon.control.IntentHandler
import me.joshlarson.jlcommon.control.Service
import java.util.*

class BuffService : Service() {
	private val timerCheckThread = ScheduledThreadPool(1, "buff-timer-check")
	private val callbackMap: MutableMap<String, BuffCallback> = HashMap()
	private val buffs = ServerData.buffs

	init {
		registerCallbacks()
	}

	private fun registerCallbacks() {
		callbackMap["removeBurstRun"] = RemoveBurstRunBuffCallback()
	}

	override fun start(): Boolean {
		timerCheckThread.start()
		return super.start()
	}

	override fun stop(): Boolean {
		timerCheckThread.stop()
		return super.stop()
	}

	@IntentHandler
	private fun handleBuffIntent(bi: BuffIntent) {
		val buffName = bi.buffName
		val buffCrc = CRC(CRC.getCrc(buffName.lowercase()))
		val buffer = bi.buffer
		val receiver = bi.receiver

		if (bi.isRemove) {
			removeBuff(receiver, buffCrc)
		} else {
			addBuff(receiver, buffCrc, buffer)
		}
	}

	@IntentHandler
	private fun handleCreatureKilledIntent(cki: CreatureKilledIntent) {
		removeAllBuffs(cki.corpse)
	}

	@IntentHandler
	private fun handlePlayerEventIntent(intent: PlayerEventIntent) {
		val event = intent.event

		if (event == PlayerEvent.PE_ZONE_IN_SERVER) {
			removeExpiredBuffs(intent.player.creatureObject)
		}
	}

	private fun removeExpiredBuffs(creatureObject: CreatureObject) {
		val crcsForExpiredBuffs = creatureObject.buffs.filter { isBuffExpired(creatureObject, it.value) }.map { it.key }

		crcsForExpiredBuffs.forEach { removeBuff(creatureObject, it) }
	}

	private fun calculatePlayTime(creature: CreatureObject): Int {
		if (creature.isPlayer) {
			creature.playerObject.updatePlayTime()
			return creature.playerObject.playTime
		}

		// NPC time is based on the current galactic time because NPCs don't have playTime
		return ProjectSWG.galacticTime.toInt()
	}

	private fun isBuffExpired(creature: CreatureObject, buff: Buff): Boolean {
		return calculatePlayTime(creature) >= buff.endTime
	}

	private fun isBuffInfinite(buffData: BuffInfo): Boolean {
		return buffData.duration < 0
	}

	private fun removeAllBuffs(creature: CreatureObject) {
		val buffCrcs = creature.buffs.keys
		buffCrcs.forEach { removeBuff(creature, it) }
	}

	private fun addBuff(receiver: CreatureObject, buffCrc: CRC, buffer: CreatureObject) {
		val buffData = buffs.getBuff(buffCrc)
		if (buffData == null) {
			StandardLog.onPlayerError(this, buffer, "buff by name '%s' could not be looked up", buffCrc.string)
			return
		}
		val groupName = buffData.group1
		val existingGroup1Buff = receiver.buffs.keys.asSequence().map { buffs.getBuff(it) }.filterNotNull().filter {
			val candidateBuffGroup1Name = it.group1
			groupName == candidateBuffGroup1Name
		}.firstOrNull()

		if (existingGroup1Buff != null) {
			val existingGroup1BuffCrc = existingGroup1Buff.crc
			val sameBuff = existingGroup1BuffCrc == buffCrc

			if (sameBuff) {
				if (isBuffInfinite(buffData)) {
					removeBuff(receiver, buffCrc)
				} else {
					// Reset timer
					removeBuff(receiver, buffCrc)
					applyBuff(receiver, buffer, buffData)
				}
			} else {
				if (buffData.priority >= existingGroup1Buff.priority) {
					removeBuff(receiver, existingGroup1BuffCrc)
					applyBuff(receiver, buffer, buffData)
				}
			}
		} else {
			applyBuff(receiver, buffer, buffData)
		}
	}

	private fun removeBuff(creature: CreatureObject, buffCrc: CRC) {
		if (!creature.buffs.containsKey(buffCrc)) return  // Obique: Used to be an assertion, however if a service sends the removal after it expires it would assert - so I just removed it.

		val buffData = buffs.getBuff(buffCrc)
		if (buffData == null) {
			StandardLog.onPlayerError(this, creature, "unable to remove buff '%s' as it could not be looked up", buffCrc.string)
			return
		}

		val removedBuff = creature.removeBuff(buffCrc)
		Objects.requireNonNull(removedBuff, "Buff must exist if being removed")
		StandardLog.onPlayerTrace(this, creature, "buff '%s' was removed", buffCrc.string)

		checkBuffEffects(buffData, creature, false)
		checkCallback(buffData, creature)
	}

	private fun applyBuff(receiver: CreatureObject, buffer: CreatureObject, buffData: BuffInfo) {
		val applyTime = calculatePlayTime(receiver)
		val buffDuration = buffData.duration.toInt()
		val endTime = applyTime + buffDuration

		val bufferPlayer = buffer.owner
		val bufferUsername = if (bufferPlayer == null) "NULL" else bufferPlayer.username
		StandardLog.onPlayerTrace(this, receiver, "received buff '%s' from %s/%s; applyTime: %d, buffDuration: %d", buffData.name, bufferUsername, buffer.objectName, applyTime, buffDuration)

		checkBuffEffects(buffData, receiver, true)
		receiver.addBuff(buffData.crc, Buff(endTime))

		sendParticleEffect(buffData.particle, receiver, "")

		scheduleBuffExpirationCheck(receiver, buffData)
	}

	private fun scheduleBuffExpirationCheck(receiver: CreatureObject, buffData: BuffInfo) {
		timerCheckThread.execute(1000L) {
			val buff = receiver.buffs[buffData.crc] ?: return@execute
			if (isBuffExpired(receiver, buff)) {
				removeBuff(receiver, buffData.crc)
			} else {
				scheduleBuffExpirationCheck(receiver, buffData)
			}
		}
	}

	private fun sendParticleEffect(effectFileName: String?, receiver: CreatureObject, hardPoint: String) {
		if (!effectFileName.isNullOrEmpty()) {
			receiver.sendObservers(PlayClientEffectObjectMessage(effectFileName, hardPoint, receiver.objectId, ""))
		}
	}

	private fun checkCallback(buffData: BuffInfo, creature: CreatureObject) {
		val callback = buffData.callback

		callbackMap[callback]?.execute(creature)
	}

	private fun checkBuffEffects(buffData: BuffInfo, creature: CreatureObject, add: Boolean) {
		for (i in 0..4) checkBuffEffect(creature, buffData.getEffectName(i), buffData.getEffectValue(i), add)
	}

	private fun checkBuffEffect(creature: CreatureObject, effectName: String?, effectValue: Double, add: Boolean) {
		if (effectName.isNullOrEmpty()) {
			return
		}

		val command = commands().isCommand(effectName) && effectValue == 1.0
		
		if (command) {
			checkCommand(add, creature, effectName)
		} else {
			checkSkillMod(add, effectName, effectValue, creature)
		}
	}

	private fun checkSkillMod(add: Boolean, effectName: String, effectValue: Double, creature: CreatureObject) {
		if (add) {
			SkillModIntent(effectName, 0, effectValue.toInt(), creature).broadcast()
		} else {
			SkillModIntent(effectName, 0, -effectValue.toInt(), creature).broadcast()
		}
	}

	private fun checkCommand(add: Boolean, creature: CreatureObject, effectName: String) {
		if (add) {
			creature.addCommand(effectName)
		} else {
			creature.removeCommand(effectName)
		}
	}
}
