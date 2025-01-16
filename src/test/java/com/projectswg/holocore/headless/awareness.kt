/***********************************************************************************
 * Copyright (c) 2025 /// Project SWG /// www.projectswg.com                       *
 *                                                                                 *
 * ProjectSWG is an emulation project for Star Wars Galaxies founded on            *
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies. *
 * Our goal is to create one or more emulators which will provide servers for      *
 * players to continue playing a game similar to the one they used to play.        *
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
package com.projectswg.holocore.headless

import com.projectswg.common.data.encodables.tangible.Posture
import com.projectswg.common.network.packets.swg.zone.SceneDestroyObject
import com.projectswg.common.network.packets.swg.zone.SceneEndBaselines
import com.projectswg.common.network.packets.swg.zone.object_controller.PostureUpdate
import com.projectswg.holocore.resources.support.objects.swg.SWGObject
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject
import me.joshlarson.jlcommon.log.Log
import java.util.concurrent.TimeUnit

fun ZonedInCharacter.waitUntilObjectDestroyed(objectId: Long) {
	player.waitForNextPacket(SceneDestroyObject::class.java, 1, TimeUnit.SECONDS) { it.objectId == objectId }
}

fun ZonedInCharacter.isAwareOf(target: SWGObject): Boolean {
	return player.creatureObject.isAwareOf(target)
}

fun ZonedInCharacter.waitUntilAwareOf(target: SWGObject) {
	if (isAwareOf(target))
		return
	player.waitForNextPacket(SceneEndBaselines::class.java, 1, TimeUnit.SECONDS) { it.objectId == target.objectId }
}

fun ZonedInCharacter.waitUntilPostureUpdate(target: CreatureObject): Posture {
	while (true) { // Naturally ends with an exception due to timeout in the waitForNextPacket line
		val postureUpdate = player.waitForNextPacket(PostureUpdate::class.java, 50, TimeUnit.MILLISECONDS) ?: throw IllegalStateException("No posture update received")
		Log.d("Received PostureUpdate for target %d  and posture %s", postureUpdate.objectId, postureUpdate.posture)
		if (postureUpdate.objectId == target.objectId)
			return postureUpdate.posture
	}
}
