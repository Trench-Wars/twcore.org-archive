/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package twcore.bots.pubsystem.module;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import twcore.bots.pubsystem.PubContext;
import twcore.bots.pubsystem.module.player.PubPlayer;
import twcore.bots.pubsystem.pubsystem;
import twcore.core.BotAction;
import twcore.core.EventRequester;
import twcore.core.events.PlayerDeath;
import twcore.core.game.Player;

/**
 * PubSystem Bounty Module
 * 
 * Allows players to place bounties on each other in pub.
 * 
 * @see http://www.twcore.org/ticket/874
 * @author SpookedOne
 */
public class BountyModule extends AbstractModule {

    private ConcurrentHashMap<String, Integer> bounties;
    
    private int minimumBounty;
    private int maximumBounty;
    private int announceDelay;
    
    private boolean isRunning;
    private boolean isAnnouncing;
    
    public BountyModule(BotAction botAction, PubContext context) {
        super(botAction, context, "Bounty");
        
        bounties = new ConcurrentHashMap<String, Integer>();
        
        isRunning = false;
        isAnnouncing = true;
        
        reloadConfig();
    }
    
    @Override
    public void start() {
        reloadConfig();
        isRunning = true;
    }

    @Override
    public void stop() {
        isRunning = false;
        bounties.clear();
    }

    @Override
    public void requestEvents(EventRequester eventRequester) {
        eventRequester.request(EventRequester.PLAYER_DEATH);
    }

    @Override
    public void handleEvent(PlayerDeath event) {
        if (isRunning) {
            Player killed = m_botAction.getPlayer(event.getKilleeID());
            Player killer = m_botAction.getPlayer(event.getKillerID());
            if (killed != null && killer != null) {
                Integer amount = bounties.get(killed.getPlayerName());
                if (amount != null) {
                    PubPlayer killerPubPlayer = context.getPlayerManager()
                            .getPlayer(killer.getPlayerName());
                    killerPubPlayer.addMoney(amount);
                    bounties.remove(killed.getPlayerName());
                    m_botAction.sendPrivateMessage(killer.getPlayerName(), 
                            "[Bounty] You have been awarded $" + amount + 
                            " for the death of " + killed.getPlayerName());
                }
            }
        }
    }
    
    
    @Override
    public void reloadConfig() {
        enabled = m_botAction.getBotSettings().getInt("bounty_enabled") == 1;
        minimumBounty = m_botAction.getBotSettings().getInt("bounty_min");
        maximumBounty = m_botAction.getBotSettings().getInt("bounty_max");
        announceDelay = m_botAction.getBotSettings().getInt("bounty_del");
    }

    @Override
    public void handleCommand(String sender, String command) {
        String in = command.toLowerCase();
        if (in.startsWith("!listbty")) {
            listBounty(sender, command);
        } else if (in.startsWith("!addbty")) {
            addBounty(sender, command);
        }
    }

    @Override
    public void handleModCommand(String sender, String command) {
    }

    @Override
    public void handleSmodCommand(String sender, String command) {
        String in = command.toLowerCase();
        if (in.startsWith("!reloadbty")) {
            stop();
            reloadConfig();
            start();
            m_botAction.sendPrivateMessage(sender, "[Bounty] Restarted.");
        }
    }

    @Override
    public String[] getHelpMessage(String sender) {
        return new String[] {
            pubsystem.getHelpLine("!listbty             -- List current bounties."),
            pubsystem.getHelpLine("!listbty <name>      -- List current bounty on <name>."),
            pubsystem.getHelpLine("!addbty <name>:<amt> -- Add <amt> of bounty to player <name>."),
        };
    }

    @Override
    public String[] getModHelpMessage(String sender) {
        return new String[] {};
    }

    @Override
    public String[] getSmodHelpMessage(String sender) {
        return new String[] {
            pubsystem.getHelpLine("!reloadbty           -- Reload the configuration file. (Restarts)"),
        };
    }
    
    private void listBounty(String sender, String command) {
        String name = null;
        if (command.length() > "!listbty ".length()) {
            name = command.substring("!listbty ".length());
        }
        
        if (name != null) {
            Player lookup = m_botAction.getFuzzyPlayer(name);
            if (lookup != null) {
                Integer bounty = bounties.get(lookup.getPlayerName());
                m_botAction.sendPrivateMessage(sender, "[Bounty] " + lookup.getPlayerName()
                        + " has a $" + (bounty==null?"0":bounty) + " bounty.");
            }
        } else {
            List<Player> players = m_botAction.getPlayingPlayers();
            int count = 0;
            for (Player p : players) {
                Integer bounty = null;
                try {
                    bounty = bounties.get(p.getPlayerName());
                } catch (ConcurrentModificationException e) {}
                if (bounty != null) {
                    m_botAction.sendPrivateMessage(sender, "[Bounty] " + p.getPlayerName()
                        + " has a $" + bounty + " bounty.");
                    count++;
                }
                
            }
            m_botAction.sendPrivateMessage(sender, "[Bounty] " + count + " current bounties.");
        }
    }
    
    private void addBounty(String sender, String command) {
        PubPlayer requester = context.getPlayerManager().getPlayer(sender);
        String[] parameters = null;
        Player deadman = null;
        
        if (command.length() > "!addbty ".length()) {
            String sub = command.substring("!addbty ".length());
            parameters = sub.split(":");
        }
        
        if (parameters != null) {
            deadman = m_botAction.getFuzzyPlayer(parameters[0]);
        }
        
        if (requester != null && deadman != null) {
            
            Integer currentAmount = bounties.get(deadman.getPlayerName());
            if (currentAmount == null) {
                currentAmount = 0;
            }
            
            try {
                Integer addition = Integer.parseInt(parameters[1]);
                
                if (addition < minimumBounty || addition > maximumBounty) {
                    throw new NumberFormatException();
                } else if (addition + currentAmount > maximumBounty) {
                    throw new NumberFormatException();
                } else if (requester.getMoney() < addition) {
                    throw new NumberFormatException();
                } else {
                    currentAmount += addition;
                    requester.removeMoney(addition);
                    bounties.put(deadman.getPlayerName(), currentAmount);
                    m_botAction.sendPrivateMessage(sender, "[Bounty] You have "
                            + "added $" + addition + " of bounty to " + deadman.getPlayerName());
                    
                    //timer delay on announce
                    if (isAnnouncing) {
                        m_botAction.sendArenaMessage("[Bounty] " + deadman + 
                                " now has a bounty of $" + currentAmount + 
                                ". Private message !listbty to TW-PubSystem to view.");
                        
                        isAnnouncing = false;
                        m_botAction.scheduleTask(new TimerTask() {
                            @Override
                            public void run() {
                                isAnnouncing = true;
                            }
                        }, announceDelay * 1000);
                    }
                }
            } catch (NumberFormatException e) {
                m_botAction.sendPrivateMessage(sender, "[Bounty] Unable to add"
                        + " that amount. Minimum $" + minimumBounty + ", Maximum $"
                        + maximumBounty);
            }
        }
    }
}
