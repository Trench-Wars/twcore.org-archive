package twcore.bots.pubsystem.module.moneysystem.item;

public class PubShipItem extends PubItem {

	private int shipNumber;

	public PubShipItem(String name, String displayName, String description, int price, int shipNumber) {
		super(name, displayName, description, price);
		this.shipNumber = shipNumber;
	}
	
	public int getShipNumber() {
		return shipNumber;
	}

}
