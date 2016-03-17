package twcore.bots.pubsystem.module.moneysystem.item;

public class PubCommandItem extends PubItem {

    private String command;

    public PubCommandItem(String name, String displayName, String description, int price, String command) {
        super(name, displayName, description, price);
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

}
