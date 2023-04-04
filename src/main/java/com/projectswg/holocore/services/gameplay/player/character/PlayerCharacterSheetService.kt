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

package com.projectswg.holocore.services.gameplay.player.character

import com.projectswg.common.network.packets.swg.zone.CharacterSheetResponseMessage
import com.projectswg.common.network.packets.swg.zone.FactionResponseMessage
import com.projectswg.holocore.intents.support.global.command.ExecuteCommandIntent
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject
import com.projectswg.holocore.resources.support.objects.swg.player.PlayerObject
import me.joshlarson.jlcommon.control.IntentHandler
import me.joshlarson.jlcommon.control.Service

class PlayerCharacterSheetService : Service() {

	@IntentHandler
	private fun handleExecuteCommandIntent(eci: ExecuteCommandIntent) {
		if (eci.command.cppCallback != "requestCharacterSheetInfo") return

		val creature = eci.source
		val player = creature.playerObject ?: return
		sendCharacterSheetResponseMessage(creature, player)
		sendFactionResponseMessage(player, creature)
	}

	private fun sendCharacterSheetResponseMessage(creature: CreatureObject, player: PlayerObject) {
		creature.sendSelf(
			CharacterSheetResponseMessage(
				lotsUsed = player.lotsAvailable - player.lotsUsed, factionCrc = creature.pvpFaction.crc, factionStatus = creature.pvpStatus.value
			)
		)
	}

	private fun sendFactionResponseMessage(player: PlayerObject, creature: CreatureObject) {
		val factionPoints = player.factionPoints
		val factionNameList = factionPoints.keys.toList()
		val factionPointList = factionNameList.map { factionPoints.getOrDefault(it, 0) }.map { it.toFloat() }
		creature.sendSelf(
			FactionResponseMessage(
				factionRank = "recruit",    // From datatables/faction/rank.iff, should be dynamic once we implement faction ranks
				rebelPoints = factionPoints.getOrDefault("rebel", 0),
				imperialPoints = factionPoints.getOrDefault("imperial", 0),
				factionNames = factionNameList,
				factionPoints = factionPointList
			)
		)
	}

}
