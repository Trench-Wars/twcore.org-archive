package twcore.bots.matchbot;

import twcore.core.*;
import twcore.misc.database.DBPlayerData;

public class InvalidStateException extends RuntimeException
{
  public InvalidStateException()
  {
    super();
  }

  public InvalidStateException(String message)
  {
    super(message);
  }
}