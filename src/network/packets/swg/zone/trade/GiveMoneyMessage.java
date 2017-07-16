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
package network.packets.swg.zone.trade;

import com.projectswg.common.network.NetBuffer;

import network.packets.swg.SWGPacket;

public class GiveMoneyMessage extends SWGPacket{
	
	public static final int CRC = com.projectswg.common.data.CRC.getCrc("GiveMoneyMessage");
	
	private int moneyAmount;
	
	public GiveMoneyMessage(int moneyAmount) {
		super();
		this.moneyAmount = moneyAmount;
	}
	
	public GiveMoneyMessage() {
		super();
	}

	@Override
	public void decode(NetBuffer data) {
		if (!super.checkDecode(data, CRC))
			return;
		moneyAmount = data.getInt();
	}

	@Override
	public NetBuffer encode() {
		NetBuffer data = NetBuffer.allocate(10);
		data.addShort(2);
		data.addInt(CRC);
		data.addInt(moneyAmount);
		return data;
	}

	public int getMoneyAmount() {
		return moneyAmount;
	}
}