package net.SubSpace.SSDR.Main;

public class ThreadCheckForUpdates implements Runnable{

	static Thread currentCheckedZone;
	static Long minutes = (long) 30;
	static boolean wait = false;
	static boolean runChecks = true;

	@Override
	public void run() 
	{
		while(runChecks)
		{		
			try {
				for(int j = 0; j < SSDR.zoneCount; j++)
				{			
					ThreadCheck.kill = false;
					ThreadIcon.isServerReachable = false;
					wait = false;
					if(currentCheckedZone != null)
					{
						if((currentCheckedZone.isInterrupted() || !currentCheckedZone.isAlive() || ThreadCheck.kill) && SSDR.ZoneUpdate[j])
						{							
							currentCheckedZone = new Thread(new ThreadCheck(SSDR.ZoneName[j],SSDR.ZoneIP[j],SSDR.ZonePort[j], false), SSDR.ZoneName[j] + "'s Downloading Thread");
							currentCheckedZone.start();
						}
						else
						{
							wait = true;
						}
						//System.out.println("While in.");
						while(!ThreadCheck.kill)
						{
							if(!runChecks)
							{
								break;
							}
							if(ThreadCheck.kill || !wait || currentCheckedZone.isInterrupted() || !currentCheckedZone.isAlive())
							{
								//System.out.println("got here.");
								break;
							}
							
						}
					//System.out.println("Out While."); // waits on thread to stop or interrupt.
						if(SSDR.ZoneUpdate[j])
						{
							currentCheckedZone = new Thread(new ThreadCheck(SSDR.ZoneName[j],SSDR.ZoneIP[j], SSDR.ZonePort[j], false), SSDR.ZoneName[j] + "'s Downloading Thread");
							currentCheckedZone.start();							
						}
					}
					else
					{
						if(SSDR.ZoneUpdate[j])
						{
							currentCheckedZone = new Thread(new ThreadCheck(SSDR.ZoneName[j],SSDR.ZoneIP[j], SSDR.ZonePort[j], false), SSDR.ZoneName[j] + "'s Downloading Thread");
							currentCheckedZone.start();							
						}
					}
					ThreadIcon.isServerReachable = true;
				}
				ThreadIcon.isDownloading = false;
				Thread.sleep(minutes * 1440 * 1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		currentCheckedZone = null;
	}
}
