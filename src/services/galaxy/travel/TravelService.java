/***********************************************************************************
t* Copyright (c) 2015 /// Project SWG /// www.projectswg.com                        *
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
package services.galaxy.travel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.projectswg.common.control.Service;
import com.projectswg.common.data.location.Location;
import com.projectswg.common.data.location.Terrain;
import com.projectswg.common.debug.Log;

import intents.chat.ChatBroadcastIntent;
import intents.network.GalacticPacketIntent;
import intents.object.ObjectCreatedIntent;
import intents.travel.TicketPurchaseIntent;
import intents.travel.TicketUseIntent;
import intents.travel.TravelPointSelectionIntent;
import network.packets.Packet;
import network.packets.swg.zone.EnterTicketPurchaseModeMessage;
import network.packets.swg.zone.PlanetTravelPointListRequest;
import network.packets.swg.zone.PlanetTravelPointListResponse;
import resources.Posture;
import resources.config.ConfigFile;
import resources.encodables.ProsePackage;
import resources.encodables.StringId;
import resources.objects.SWGObject;
import resources.objects.SpecificObject;
import resources.objects.creature.CreatureObject;
import resources.objects.staticobject.StaticObject;
import resources.objects.tangible.OptionFlag;
import resources.player.Player;
import resources.server_info.DataManager;
import resources.server_info.SdbLoader;
import resources.server_info.SdbLoader.SdbResultSet;
import resources.sui.SuiButtons;
import resources.sui.SuiListBox;
import resources.sui.SuiMessageBox;

public class TravelService extends Service {
	
	private final TravelHelper travel;
	private final double ticketPriceFactor;
	
	public TravelService() {
		this.travel = new TravelHelper();
		this.ticketPriceFactor = DataManager.getConfig(ConfigFile.FEATURES).getDouble("TICKET-PRICE-FACTOR", 1);
		
		loadTravelPoints();
		
		registerForIntent(TravelPointSelectionIntent.class, tpsi -> handlePointSelection(tpsi));
		registerForIntent(GalacticPacketIntent.class, gpi -> handleTravelPointRequest(gpi));
		registerForIntent(TicketPurchaseIntent.class, tpi -> handleTicketPurchase(tpi));
		registerForIntent(TicketUseIntent.class, tui -> handleTicketUse(tui));
		registerForIntent(ObjectCreatedIntent.class, oci -> handleObjectCreation(oci));
	}
	
	@Override
	public boolean start() {
		travel.start();
		return super.start();
	}
	
	@Override
	public boolean stop() {
		travel.stop();
		return super.stop();
	}
	
	/**
	 * Travel points are loaded from serverdata/static/travel.sdb
	 * A travel point represents a travel destination.
	 * 
	 * @return true if all points were loaded succesfully and false if not.
	 */
	private void loadTravelPoints() {
		SdbLoader loader = new SdbLoader();
		try (SdbResultSet set = loader.load(new File("serverdata/travel/travel.sdb"))) {
			while (set.next()) {
				loadTravelPoint(set);
			}
		} catch (IOException e) {
			Log.e("Failed to load a travel point");
			Log.e(e);
		}
	}
	
	private void loadTravelPoint(SdbResultSet set) {
		String pointName = set.getText("name");
		double x = set.getReal("x");
		double z = set.getReal("z");
		String type = set.getText("type");
		Terrain travelPlanet = Terrain.getTerrainFromName(set.getText("planet"));
		if (travelPlanet == null) {
			Log.e("Invalid planet in travel.sdb: %s", set.getText("planet"));
			return;
		}
		
		TravelPoint point = new TravelPoint(pointName, new Location(x, 0, z, travelPlanet), type.endsWith("starport"), true);
		TravelGroup group;
		switch (type) {
			case "shuttleport":
				group = travel.getTravelGroup(SpecificObject.SO_TRANSPORT_SHUTTLE.getTemplate());
				break;
			case "starport":
				group = travel.getTravelGroup(SpecificObject.SO_TRANSPORT_STARPORT.getTemplate());
				break;
			case "theed_starport":
				group = travel.getTravelGroup(SpecificObject.SO_TRANSPORT_STARPORT_THEED.getTemplate());
				break;
			default:
				Log.w("Invalid travel point type: %s", type);
				return;
		}
		group.addTravelPoint(point);
		point.setGroup(group);
		travel.addTravelPoint(point);
	}
	
	private List<Integer> getAdditionalCosts(Terrain objectTerrain, Collection<TravelPoint> points) {
		List<Integer> additionalCosts = new ArrayList<>();
		
		for (TravelPoint point : points) {
			additionalCosts.add(getAdditionalCost(objectTerrain, point.getTerrain()));
		}
		
		return additionalCosts;
	}
	
	private int getAdditionalCost(Terrain departureTerrain, Terrain destinationTerrain) {
		if (ticketPriceFactor <= 0) {
			return -travel.getTravelFee(departureTerrain, destinationTerrain);
		} else {
			// TODO implement algorithm for the extra ticket cost.
			return 10;
		}
	}
	
	private void handlePointSelection(TravelPointSelectionIntent tpsi) {
		CreatureObject traveler = tpsi.getCreature();
		
		traveler.sendSelf(new EnterTicketPurchaseModeMessage(traveler.getTerrain().getName(), travel.getNearestTravelPoint(traveler).getName(), tpsi.isInstant()));
	}
	
	private void handleTravelPointRequest(GalacticPacketIntent i) {
		Packet p = i.getPacket();
		
		if (p instanceof PlanetTravelPointListRequest) {
			String planetName = ((PlanetTravelPointListRequest) p).getPlanetName();
			Player player = i.getPlayer();
			Terrain to = Terrain.getTerrainFromName(planetName);
			if (to == null) {
				Log.e("Unknown terrain in PlanetTravelPointListRequest: %s", planetName);
				return;
			}
			List<TravelPoint> pointsForPlanet = travel.getAvailableTravelPoints(player.getCreatureObject(), to);
			Collections.sort(pointsForPlanet);
			
			TravelPoint nearest = travel.getNearestTravelPoint(player.getCreatureObject());
			if (pointsForPlanet.remove(nearest))
				pointsForPlanet.add(0, nearest); // Yes ... adding it to the beginning of the list because I hate the client
				
			player.sendPacket(new PlanetTravelPointListResponse(planetName, pointsForPlanet, getAdditionalCosts(player.getCreatureObject().getTerrain(), pointsForPlanet)));
		}
	}
	
	private void handleTicketPurchase(TicketPurchaseIntent i) {
		CreatureObject purchaser = i.getPurchaser();
		TravelPoint nearestPoint = travel.getNearestTravelPoint(purchaser);
		TravelPoint destinationPoint = travel.getDestinationPoint(Terrain.getTerrainFromName(i.getDestinationPlanet()), i.getDestinationName());
		Player purchaserOwner = purchaser.getOwner();
		boolean roundTrip = i.isRoundTrip();
		
		if (nearestPoint == null || destinationPoint == null || !travel.isValidRoute(nearestPoint.getTerrain(), destinationPoint.getTerrain())) {
			Log.w("Unable to purchase ticket! Nearest Point: %s  Destination Point: %s", nearestPoint, destinationPoint);
			return;
		}
		
		int ticketPrice = getTotalTicketPrice(nearestPoint.getTerrain(), destinationPoint.getTerrain(), roundTrip);
		if (purchaser.removeFromBankAndCash(ticketPrice)) {
			new ChatBroadcastIntent(purchaserOwner, String.format("You succesfully make a payment of %d credits to the Galactic Travel Commission.", ticketPrice)).broadcast();
		} else {
			showMessageBox(purchaserOwner, "short_funds");
			return;
		}
		
		travel.grantTickets(purchaser, nearestPoint, destinationPoint, roundTrip);
		showMessageBox(purchaserOwner, "ticket_purchase_complete");
	}
	
	private void handleTicketUse(TicketUseIntent i) {
		CreatureObject creature = i.getPlayer().getCreatureObject();
		
		TravelPoint nearestPoint = travel.getNearestTravelPoint(creature);
		if (nearestPoint == null || nearestPoint.getShuttle() == null || !nearestPoint.isWithinRange(creature)) {
			sendTravelMessage(creature, "@travel:boarding_too_far");
			return;
		}
		
		switch (nearestPoint.getGroup().getStatus()) {
			case GROUNDED:
				if (i.getTicket() == null)
					handleTicketUseSui(i.getPlayer());
				else
					travel.handleTicketUse(i.getPlayer(), i.getTicket(), travel.getNearestTravelPoint(i.getTicket()), travel.getDestinationPoint(i.getTicket()));
				break;
			case LANDING:
				sendTravelMessage(creature, "@travel/travel:shuttle_begin_boarding");
				break;
			case LEAVING:
				sendTravelMessage(creature, "@travel:shuttle_not_available");
				break;
			case AWAY:
				sendTravelMessage(creature, "@travel/travel:shuttle_board_delay", "DI", nearestPoint.getGroup().getTimeRemaining());
				break;
		}
	}
	
	private void handleTicketUseSui(Player player) {
		List<SWGObject> usableTickets = travel.getTickets(player.getCreatureObject());
		
		if (usableTickets.isEmpty()) {	// They don't have a valid ticket.
			new ChatBroadcastIntent(player, "@travel:no_ticket_for_shuttle").broadcast();
		} else {
			SuiListBox ticketBox = new SuiListBox(SuiButtons.OK_CANCEL, "@travel:select_destination", "@travel:select_destination");
			
			for (SWGObject usableTicket : usableTickets) {
				TravelPoint destinationPoint = travel.getDestinationPoint(usableTicket);
				
				ticketBox.addListItem(destinationPoint.getSuiFormat(), destinationPoint);
			}
			
			ticketBox.addOkButtonCallback("handleSelectedItem", (callbackPlayer, actor, event, parameters) -> {
				int row = SuiListBox.getSelectedRow(parameters);
				SWGObject ticket = usableTickets.get(row);
				TravelPoint nearestPoint = travel.getNearestTravelPoint(ticket);
				TravelPoint destinationPoint = (TravelPoint) ticketBox.getListItem(row).getObject();
				travel.handleTicketUse(callbackPlayer, ticket, nearestPoint, destinationPoint);
			});
			ticketBox.display(player);
		}
	}
	
	private int getTotalTicketPrice(Terrain departurePlanet, Terrain arrivalPlanet, boolean roundTrip) {
		if (ticketPriceFactor <= 0)
			return 0;
		
		int totalPrice = 0;
		totalPrice += travel.getTravelFee(departurePlanet, arrivalPlanet);	// The base price
		totalPrice += getAdditionalCost(departurePlanet, arrivalPlanet);	// The extra amount to pay.
		
		if (roundTrip)
			totalPrice += travel.getTravelFee(arrivalPlanet, departurePlanet);
		
		return totalPrice;
	}
	
	private void handleObjectCreation(ObjectCreatedIntent i) {
		SWGObject object = i.getObject();
		
		// There are non-functional shuttles, which are StaticObject. We run an instanceof check to make sure that we ignore those.
		if (travel.getTravelGroup(object.getTemplate()) != null && !(object instanceof StaticObject)) {
			TravelPoint pointForShuttle = travel.getNearestTravelPoint(object);
			CreatureObject shuttle = (CreatureObject) object;
			
			if (pointForShuttle == null) {
				Log.w("No point for shuttle at location: " + object.getWorldLocation());
				return;
			}
			// Assign the shuttle to the nearest travel point
			pointForShuttle.setShuttle(shuttle);
			
			shuttle.setOptionFlags(OptionFlag.INVULNERABLE);
			shuttle.setPosture(Posture.UPRIGHT);
			shuttle.setShownOnRadar(false);
		} else if (object.getTemplate().equals(SpecificObject.SO_TICKET_COLLETOR.getTemplate())) {
			TravelPoint pointForCollector = travel.getNearestTravelPoint(object);
			
			if (pointForCollector == null) {
				Log.w("No point for collector at location: " + object.getWorldLocation());
				return;
			}
			
			pointForCollector.setCollector(object);
		}
	}
	
	private void sendTravelMessage(CreatureObject creature, String message) {
		new ChatBroadcastIntent(creature.getOwner(), message).broadcast();
	}
	
	private void sendTravelMessage(CreatureObject creature, String str, String key, Object obj) {
		new ChatBroadcastIntent(creature.getOwner(), new ProsePackage(new StringId(str), key, obj)).broadcast();
	}
	
	private void showMessageBox(Player receiver, String message) {
		// Create the SUI window
		SuiMessageBox messageBox = new SuiMessageBox(SuiButtons.OK, "STAR WARS GALAXIES", "@travel:" + message);
		
		// Display the window to the purchaser
		messageBox.display(receiver);
	}
	
}
