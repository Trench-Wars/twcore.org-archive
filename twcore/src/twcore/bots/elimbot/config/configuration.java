package twcore.bots.elimbot.config;

import java.util.HashMap;

import twcore.core.BotAction;
import twcore.core.BotSettings;

public class configuration {
	
	private BotAction m_botAction;
	private BotSettings config;
	
	private HashMap<String, fileConfiguration> configurations = new HashMap<String, fileConfiguration>();
	private String arena;
	private String chat;
	
	private int currentConfig = 1;
	private int playerMin = 3;
	private boolean allowLagouts = true;
	
	private boolean rulesShown = false;
	
	public configuration(BotAction m_botAction, BotSettings config) {
		this.m_botAction = m_botAction;
		this.config = config;
		this.initialize();
	}
	
	private void initialize() {
		arena = config.getString("arena");
		chat = config.getString("chat");
		playerMin = config.getInt("playermin");
		allowLagouts = (config.getInt("lagouts") == 1);
		
		// Load the elim configurations
		int i = 1;
		
		while(config.getString("config"+i) != null) {
			String name = config.getString("config"+i);
			
			fileConfiguration elimConf = new fileConfiguration(i,name);
			elimConf.setShips(config.getString(name+"-ships"));
			elimConf.setShipsDefault(config.getInt(name+"-ships-default"));
			elimConf.setDeathLimit(config.getString(name+"-deathlimit"));
			elimConf.setDeathLimitDefault(config.getInt(name+"-deathlimit-default"));
			elimConf.setFullname(config.getString(name+"-fullname"));
			if(config.getString(name+"-spawn") != null) {
				elimConf.setSpawn(config.getString(name+"-spawn"));
			}
			
			configurations.put(String.valueOf(i), elimConf);
			
			i++;
		}
	}
	
	public String getArena() {
		return this.arena;
	}
	
	public String getChat() {
		return this.chat;
	}
	
	public int getPlayerMin() {
		return this.playerMin;
	}
	
	/**
	 * Returns the configuration associated with the key
	 * @param key The configuration key, always of the format "config#"
	 * @return elimConfiguration
	 */
	public fileConfiguration getConfig(String key) {
		return configurations.get(key);
	}

	/**
	 * Returns the current configuration
	 * @return elimConfiguration
	 */
	public fileConfiguration getCurrentConfig() {
		return configurations.get(String.valueOf(this.currentConfig));
	}
	
	/**
	 * Advance the current configuration to the next one or to the first configuration
	 */
	public void nextConfiguration() {
		this.currentConfig++;
		
		if(configurations.get(this.currentConfig)==null) {
			this.currentConfig = 1;
		}
		
		this.setRulesShown(false);
	}

	/**
	 * @return the rulesShown
	 */
	public boolean isRulesShown() {
		return rulesShown;
	}

	/**
	 * @param rulesShown the rulesShown to set
	 */
	public void setRulesShown(boolean rulesShown) {
		this.rulesShown = rulesShown;
	}

	/**
	 * @return the allowLagouts
	 */
	public boolean isAllowLagouts() {
		return allowLagouts;
	}
	
	
}