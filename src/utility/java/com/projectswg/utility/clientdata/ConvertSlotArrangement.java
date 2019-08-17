/***********************************************************************************
 * Copyright (c) 2019 /// Project SWG /// www.projectswg.com                       *
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

package com.projectswg.utility.clientdata;

import com.projectswg.common.data.swgiff.parsers.SWGParser;
import com.projectswg.common.data.swgiff.parsers.slots.SlotArrangementParser;
import com.projectswg.holocore.utilities.SdbGenerator;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.stream.Collectors;

class ConvertSlotArrangement implements Converter {
	
	@Override
	public void convert() {
		System.out.println("Converting slot arrangements...");
		
		try (SdbGenerator sdb = new SdbGenerator(new File("serverdata/abstract/slot_arrangements.sdb"))) {
			sdb.writeColumnNames("iff", "slots");
			Converter.traverseFiles(this, new File("clientdata/abstract/slot/arrangement"), sdb, f -> f.getName().endsWith(".iff"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void convertFile(SdbGenerator sdb, File file) throws IOException {
		SlotArrangementParser arrangement = SWGParser.parse(file);
		Objects.requireNonNull(arrangement, "Failed to load clientdata");
		
		String iff = file.getAbsolutePath();
		String slotArrangement = arrangement.getArrangement().stream().map(slots -> String.join(",", slots)).collect(Collectors.joining(";"));
		sdb.writeLine(iff.substring(iff.indexOf("abstract/slot/arrangement/")+26, iff.lastIndexOf('.')), slotArrangement);
	}
	
}
