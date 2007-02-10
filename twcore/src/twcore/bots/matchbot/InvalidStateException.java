package twcore.bots.matchbot;

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