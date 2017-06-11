/************************************************************************************
 * Copyright (c) 2015 /// Project SWG /// www.projectswg.com                        *
 *                                                                                  *
 * ProjectSWG is the first NGE emulator for Star Wars Galaxies founded on           *
 * July 7th, 2011 after SOE announced the official shutdown of Star Wars Galaxies.  *
 * Our goal is to create an emulator which will provide a server for players to     *
 * continue playing a game similar to the one they used to play. We are basing      *
 * it on the final publish of the game prior to end-game events.                    *
 *                                                                                  *
 * This file is part of Holocore.                                                   *
 *                                                                                  *
 * -------------------------------------------------------------------------------- *
 *                                                                                  *
 * Holocore is free software: you can redistribute it and/or modify                 *
 * it under the terms of the GNU Affero General Public License as                   *
 * published by the Free Software Foundation, either version 3 of the               *
 * License, or (at your option) any later version.                                  *
 *                                                                                  *
 * Holocore is distributed in the hope that it will be useful,                      *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of                   *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                    *
 * GNU Affero General Public License for more details.                              *
 *                                                                                  *
 * You should have received a copy of the GNU Affero General Public License         *
 * along with Holocore.  If not, see <http://www.gnu.org/licenses/>.                *
 *                                                                                  *
 ***********************************************************************************/
package services.crafting.survey;

import java.util.List;

import com.projectswg.common.data.location.Terrain;
import com.projectswg.common.debug.Log;

import resources.objects.creature.CreatureObject;
import services.crafting.resource.galactic.GalacticResource;
import services.crafting.resource.galactic.GalacticResourceSpawn;
import services.crafting.resource.galactic.storage.GalacticResourceContainer;

public class SampleSession {
	
	private final CreatureObject creature;
	private final GalacticResource resource;
	
	public SampleSession(CreatureObject creature, GalacticResource resource) {
		this.creature = creature;
		this.resource = resource;
	}
	
	public GalacticResource getResource() {
		return resource;
	}
	
	public void startSession() {
		double concentration = getConcentration(creature);
		Log.d("%s started a sample session with %s and concentration %.1f", creature.getObjectName(), resource.getName(), concentration);
	}
	
	public void stopSession() {
		
	}
	
	private double getConcentration(CreatureObject creature) {
		List<GalacticResourceSpawn> spawns = GalacticResourceContainer.getContainer().getTerrainResourceSpawns(resource, creature.getTerrain());
		return getConcentration(spawns, creature.getTerrain(), creature.getX(), creature.getZ());
	}
	
	private double getConcentration(List<GalacticResourceSpawn> spawns, Terrain terrain, double x, double z) {
		double concentration = 0;
		for (GalacticResourceSpawn spawn : spawns) {
			concentration += spawn.getConcentration(terrain, x, z) / 100.0;
		}
		return concentration;
	}
	
}
