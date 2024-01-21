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

@file:Suppress("NOTHING_TO_INLINE")
package com.projectswg.holocore.intents.gameplay.player.quest

import com.projectswg.holocore.resources.support.data.server_info.loader.QuestLoader.QuestTaskInfo
import com.projectswg.holocore.resources.support.global.player.Player
import com.projectswg.holocore.resources.support.objects.swg.SWGObject
import me.joshlarson.jlcommon.control.Intent

data class GrantQuestIntent(val player: Player, val questName: String): Intent() {
	companion object {
		@JvmStatic inline fun broadcast(player: Player, questName: String) = GrantQuestIntent(player, questName).broadcast()
	}
}

data class AbandonQuestIntent(val player: Player, val questName: String): Intent() {
	companion object {
		@JvmStatic inline fun broadcast(player: Player, questName: String) = AbandonQuestIntent(player, questName).broadcast()
	}
}

data class CompleteQuestIntent(val player: Player, val questName: String): Intent() {
	companion object {
		@JvmStatic inline fun broadcast(player: Player, questName: String) = CompleteQuestIntent(player, questName).broadcast()
	}
}

data class QuestRetrieveItemIntent(val player: Player, val questName: String, val task: QuestTaskInfo, val item: SWGObject): Intent() {
	companion object {
		@JvmStatic inline fun broadcast(player: Player, questName: String, task: QuestTaskInfo, item: SWGObject) = QuestRetrieveItemIntent(player, questName, task, item).broadcast()
	}
}
