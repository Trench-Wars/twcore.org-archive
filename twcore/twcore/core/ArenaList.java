package twcore.core;

import java.io.*;
import java.util.*;

/*
47 - Arena list
Field    Length    Description
0        1        Type byte

Repeats until end of packet:
1        ?        Arena name
?        1        \0
?        1        Arena size
?        1        \0
End of repeated section
*/

public class ArenaList extends SubspaceEvent
{
	Map m_arenaList;

	public ArenaList(ByteArray array)
	{
		m_byteArray = array;
		m_eventType = EventRequester.ARENA_LIST; //sets the event type in the superclass
		
		int i = 1;
		String name;
		int size;

		m_arenaList = Collections.synchronizedMap(new HashMap());

		while (i < array.size())
		{
			name = array.readNullTerminatedString(i);
			i += name.length();
			i += 1; // For the terminating null
			size = array.readByte(i);
			i += 1; // For the size
			i += 1; // For the terminating null
			m_arenaList.put(name.toLowerCase(), new Integer(size));
		}
	}

	public String[] getArenaNames()
	{
		String[] arena = new String[m_arenaList.size()];
		Iterator i = m_arenaList.keySet().iterator();
		for (int x = 0; i.hasNext(); x++)
		{
			arena[x] = (String) i.next();
		}
		return arena;
	}

	public int getSizeOfArena(String arenaName)
	{
		return Math.abs(((Integer) m_arenaList.get(arenaName.toLowerCase())).intValue());
	}

	public String getCurrentArenaName()
	{
		Iterator i = m_arenaList.keySet().iterator();
		while (i.hasNext())
		{
			String key = (String) i.next();
			Integer value = (Integer) m_arenaList.get(key);
			if (value.intValue() < 0)
			{
				return key;
			}
		}
		return null;
	}
	public Map getArenaList()
	{
		return m_arenaList;
	}
}
