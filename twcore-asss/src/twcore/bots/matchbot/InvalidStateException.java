package twcore.bots.matchbot;

@SuppressWarnings("serial")
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