package twcore.bots.pubhub;

import twcore.core.*;

public abstract class PubBotModule extends Module
{
  private String ipcChannel;
  private String pubHubName;

  public void initializeModule(String ipcChannel, String pubHubName)
  {
    this.ipcChannel = ipcChannel;
    this.pubHubName = pubHubName;
  }

  public String getIPCChannel()
  {
    return ipcChannel;
  }

  public String getPubHubName()
  {
    return pubHubName;
  }
}