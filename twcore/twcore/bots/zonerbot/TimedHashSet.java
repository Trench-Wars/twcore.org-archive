package twcore.bots.zonerbot;

import java.util.*;

public class TimedHashSet implements Set
{
  public static final int PERMANANT_OBJECT = -1;
  private TimedAction timedAction;
  private HashMap entries;
  private Timer timer;

  /**
   * This method initializes a TimedHashSet instance with a certain timedAction.
   *
   * @param timedAction is the action that is taken when an object expires.
   */
  public TimedHashSet(TimedAction timedAction)
  {
    this.timedAction = timedAction;
    entries = new HashMap();
    timer = new Timer();
  }

  /**
   * This method initializes a TimedHashSet instance with no timedAction.
   */
  public TimedHashSet()
  {
    this(null);
  }

  /**
   * This method gets the number of elements in the TimedHashSet.
   *
   * @return the size of the TimedHashSet is returned.
   */
  public int size()
  {
    return entries.size();
  }

  /**
   * This method checks to see if the TimedHashSet is empty.
   *
   * @return true is returned if the TimedHashSet is empty.
   */
  public boolean isEmpty()
  {
    return entries.isEmpty();
  }

  /**
   * This method checks to see if an Object is contained in the set.
   *
   * @param o is the object to check.
   * @return true is returned if the Object is contained in the set.
   */
  public boolean contains(Object o)
  {
    return entries.containsKey(o);
  }

  /**
   * This method gets an iterator to iterate over all of the entries in the set.
   *
   * @return an iterator is returned.
   */
  public Iterator iterator()
  {
    Set set = entries.keySet();
    return set.iterator();
  }

  /**
   * This method places all of the elements of the set into a new array.
   *
   * @return an array containing all of the elements in the set is returned.
   */
  public Object[] toArray()
  {
    Set set = entries.keySet();
    return set.toArray();
  }

  /**
   * This method places all of the elements of the set into an array.
   *
   * @param array is the array to place the elements into.
   * @return an array containing all of the elements in the set is returned.
   */
  public Object[] toArray(Object[] array)
  {
    Set set = entries.keySet();
    return set.toArray(array);
  }

  /**
   * This method adds an element to the TimedSet with a specific lifetime.
   *
   * @param o is the object to add.
   * @param lifetime is the number of milliseconds that the object will last in
   * the set for.
   * @return true is returned if the object is successfully added.
   */
  public boolean add(Object o, long lifetime)
  {
    EntryTimerTask entryTimerTask = new EntryTimerTask(o, lifetime);

    if(entries.containsKey(o))
      return false;
    if(lifetime >= 0)
      timer.schedule(entryTimerTask, lifetime);
    entries.put(o, entryTimerTask);
    return true;
  }

  /**
   * This method adds an object to the Set.  The object is permanant and will
   * not be removed until the remove method is called on it.
   *
   * @param o is the object to add.
   * @return true is returned if the set is changed as a result of the
   * operation.
   */
  public boolean add(Object o)
  {
    return add(o, PERMANANT_OBJECT);
  }

  /**
   * This method removes an object from the Set.
   *
   * @param o is the object to remove.
   * @return true is returned if the set is changed as a result of the
   * operation.
   */
  public boolean remove(Object o)
  {
    EntryTimerTask entryTimerTask = (EntryTimerTask) entries.remove(o);

    if(entryTimerTask == null)
      return false;
    entryTimerTask.cancel();
    return true;
  }

  /**
   * This method checks to see if all of the elements of a Collection are
   * contained in the Set.
   *
   * @param c is the collection to check.
   * @return true is returned if the all of the elements of c are contained
   * in the Set.
   */
  public boolean containsAll(Collection c)
  {
    Set s = entries.keySet();
    return s.containsAll(c);
  }

  /**
   * This method adds all of the elements of a collection into the Set with the
   * specified lifetime.
   *
   * @param c is the Collection of elements to add.
   * @param lifetime is the lifetime of the elements.
   * @return true is returned if the set is changed as a result of the
   * operation.
   */
  public boolean addAll(Collection c, long lifetime)
  {
    Iterator iterator = c.iterator();
    int oldSize = size();

    while(iterator.hasNext())
      add(iterator.next(), lifetime);
    return oldSize != size();
  }

  /**
   * This method adds all of the elements of a collection to the Set.  The
   * items are entered in as permanant objects.
   *
   * @param c is the collection of elements to add.
   * @return true is returned if the set is changed as a result of the
   * operation.
   */
  public boolean addAll(Collection c)
  {
    return addAll(c, PERMANANT_OBJECT);
  }

  /**
   * This method checks to see if all of the elements in a collection are
   * contained in the set.
   *
   * @param c is the collection of elements to check.
   * @return true is returned if all of the elements of c are in the set.
   */
  public boolean retainAll(Collection c)
  {
    Iterator iterator = c.iterator();

    while(iterator.hasNext())
      if(!contains(iterator.next()))
         return false;
    return true;
  }

  /**
   * This method removes all of the elements that are in a collection from the
   * set.
   *
   * @param c is the collection of elements to remove.
   * @return true is returned if the set is changed as a result of the
   * operation.
   */
  public boolean removeAll(Collection c)
  {
    Iterator iterator = c.iterator();
    int oldSize = size();

    while(iterator.hasNext())
      remove(iterator.next());
    return oldSize != size();
  }

  /**
   * This method clears all of the elements of the set.
   */
  public void clear()
  {
    Set s = entries.keySet();
    Iterator iterator = s.iterator();

    while(iterator.hasNext())
      remove(iterator.next());
  }

  /**
   * This method gets the lifetime of an object.
   *
   * @param o is the object to check.
   * @return the number of milliseconds that the object will stay alive for is
   * returned.
   */
  public long getLifetime(Object o)
  {
    EntryTimerTask entryTimerTask = (EntryTimerTask) entries.get(o);

    if(entryTimerTask == null)
      throw new IllegalArgumentException("Object not found in set.");
    return entryTimerTask.getLifetime();
  }

  /**
   * This method gets the time remaining until an object expires.
   *
   * @param o is the object to check.
   * @return the number of milliseconds until the object is removed from the set
   * is returned.
   */
  public long getTimeRemaining(Object o)
  {
    EntryTimerTask entryTimerTask = (EntryTimerTask) entries.get(o);

    if(entryTimerTask == null)
      throw new IllegalArgumentException("Object not found in set.");
    return entryTimerTask.getTimeRemaining();
  }

  /**
   * This method gets a String containing the time remaining until an object expires.
   *
   * @param o is the object to check.
   * @return the time remaining until the object expires is returned.  It is returned
   * in the form of "XX mins and YY secs".
   */
  public String getTimeRemainingString(Object o)
  {
    EntryTimerTask entryTimerTask = (EntryTimerTask) entries.get(o);

    if(entryTimerTask == null)
      throw new IllegalArgumentException("Object not found in set.");
    return entryTimerTask.toString();
  }

  /**
   * This method gets the time that has elapsed since the object has been put
   * into the set.
   *
   * @param o is the object to check.
   * @return the number of milliseconds that the object has been in the set is
   * returned.
   */
  public long getTimeElapsed(Object o)
  {
    EntryTimerTask entryTimerTask = (EntryTimerTask) entries.get(o);

    if(entryTimerTask == null)
      throw new IllegalArgumentException("Object not found in set.");
    return entryTimerTask.getTimeElapsed();
  }

  /**
   * <p>Title: </p>EntryTimerTask
   * <p>Description: </p>This class is the timerTask instance that keeps track
   * of when an entry will expire.  Once the timerTasks run method is called,
   * the element is removed from the set.
   * <p>Copyright: Copyright (c) 2004</p>
   * <p>Company: </p>For SSCU Trench Wars
   * @author Cpt.Guano!
   * @version 1.0
   */
  private class EntryTimerTask extends DetailedTimerTask
  {
    private Object o;
    private long startTime;

    /**
     * This method initlaizes an EntryTimerTask to a specific object and with a
     * specific lifetime.
     *
     * @param o is the object to keep track of.
     * @param lifetime is the lifetime of the object.
     */
    public EntryTimerTask(Object o, long lifetime)
    {
      super(lifetime);
      this.o = o;
      startTime = System.currentTimeMillis();
    }

    /**
     * This method is called when the object expires.  It removes the entry
     * from the set.  If a TimedAction object has been keyed to the Set, the
     * objectExpired method will be called after the object is removed.
     */
    public void run()
    {
      remove(o);
      if(timedAction != null)
        timedAction.objectExpired(o);
    }
  }
}