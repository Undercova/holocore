/***********************************************************************************
 * Copyright (c) 2023 /// Project SWG /// www.projectswg.com                       *
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
package com.projectswg.holocore.services.gameplay.player.quest

import com.projectswg.common.data.location.Location
import com.projectswg.common.network.packets.swg.login.creation.ClientCreateCharacter
import com.projectswg.common.network.packets.swg.zone.CommPlayerMessage
import com.projectswg.common.network.packets.swg.zone.object_controller.quest.QuestCompletedMessage
import com.projectswg.common.network.packets.swg.zone.object_controller.quest.QuestTaskCounterMessage
import com.projectswg.common.network.packets.swg.zone.server_ui.SuiCreatePageMessage
import com.projectswg.holocore.intents.gameplay.combat.RequestCreatureDeathIntent
import com.projectswg.holocore.intents.gameplay.player.quest.GrantQuestIntent
import com.projectswg.holocore.resources.support.data.server_info.loader.DataLoader
import com.projectswg.holocore.resources.support.data.server_info.loader.npc.NpcStaticSpawnLoader
import com.projectswg.holocore.resources.support.global.player.AccessLevel
import com.projectswg.holocore.resources.support.global.zone.creation.CharacterCreation
import com.projectswg.holocore.resources.support.global.zone.sui.SuiMessageBox
import com.projectswg.holocore.resources.support.npc.spawn.NPCCreator
import com.projectswg.holocore.resources.support.npc.spawn.SimpleSpawnInfo
import com.projectswg.holocore.resources.support.npc.spawn.Spawner
import com.projectswg.holocore.resources.support.objects.ObjectCreator
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureDifficulty
import com.projectswg.holocore.resources.support.objects.swg.custom.AIObject
import com.projectswg.holocore.services.gameplay.combat.CombatDeathblowService
import com.projectswg.holocore.services.gameplay.player.experience.skills.SkillService
import com.projectswg.holocore.services.support.global.zone.sui.SuiService
import com.projectswg.holocore.test.resources.GenericPlayer
import com.projectswg.holocore.test.runners.TestRunnerSynchronousIntents
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class QuestTaskTypeTest : TestRunnerSynchronousIntents() {

	@BeforeEach
	fun setUp() {
		registerService(QuestService())
		registerService(SuiService())
		registerService(SkillService())
		registerService(CombatDeathblowService())
	}

	@Test
	@DisplayName("quest.task.ground.show_message_box")
	fun showMessageBox() {
		val player = createPlayer()

		GrantQuestIntent.broadcast(player, "quest/c_newbie_start")    // This quest immediately wants to display a SUI message box

		val suiCreatePageMessage = player.waitForNextPacket(SuiCreatePageMessage::class.java)
		assertNotNull(suiCreatePageMessage)
		assertEquals(SuiMessageBox::class, suiCreatePageMessage!!.window::class)
	}

	@Test
	@DisplayName("quest.task.ground.comm_player")
	fun commMessage() {
		val player = createPlayer()

		GrantQuestIntent.broadcast(player, "quest/test_comm_player")

		val commPlayerMessage = player.waitForNextPacket(CommPlayerMessage::class.java)
		assertNotNull(commPlayerMessage)
		assertEquals(CommPlayerMessage::class, commPlayerMessage!!::class)
	}

	@Test
	@DisplayName("quest.task.ground.destroy_multi")
	fun destroyMulti() {
		val player = createPlayer()
		GrantQuestIntent.broadcast(player, "quest/test_destroy_multiple")
		val declareRequiredKillCount = player.waitForNextPacket(QuestTaskCounterMessage::class.java)
		assertNotNull(declareRequiredKillCount, "Failed to receive initial required kill count in time")
		val womprats = spawnNPCs("creature_womprat", player.creatureObject.location, 3)

		womprats.forEach { womprat ->
			RequestCreatureDeathIntent.broadcast(player.creatureObject, womprat)
			val killCountUpdate = player.waitForNextPacket(QuestTaskCounterMessage::class.java)
			assertNotNull(killCountUpdate, "Failed to receive kill count update in time")
		}

		val questCompletedMessage = player.waitForNextPacket(QuestCompletedMessage::class.java)
		assertNotNull(questCompletedMessage, "Failed to receive QuestCompletedMessage in time")
	}

	private fun spawnNPCs(npcId: String, location: Location, amount: Int): Collection<AIObject> {
		val egg = ObjectCreator.createObjectFromTemplate("object/tangible/ground_spawning/shared_patrol_spawner.iff")
		egg.moveToContainer(null, location)

		val spawnInfo = SimpleSpawnInfo.builder()
			.withNpcId(npcId)
			.withDifficulty(CreatureDifficulty.NORMAL)
			.withMinLevel(1)
			.withMaxLevel(1)
			.withLocation(location)
			.withAmount(amount)
			.withSpawnerFlag(NpcStaticSpawnLoader.SpawnerFlag.ATTACKABLE)
			.build()

		return NPCCreator.createAllNPCs(Spawner(spawnInfo, egg))
	}

	private fun createPlayer(): GenericPlayer {
		val player = GenericPlayer()
		val clientCreateCharacter = ClientCreateCharacter()
		clientCreateCharacter.biography = ""
		clientCreateCharacter.clothes = "combat_brawler"
		clientCreateCharacter.race = "object/creature/player/shared_human_male.iff"
		clientCreateCharacter.name = "Testing Character"
		val characterCreation = CharacterCreation(player, clientCreateCharacter)

		val mosEisley = DataLoader.zoneInsertions().getInsertion("tat_moseisley")
		val creatureObject = characterCreation.createCharacter(AccessLevel.PLAYER, mosEisley)
		creatureObject.owner = player

		return player
	}
}