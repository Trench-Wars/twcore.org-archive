package twcore.bots.hockeybot;

import java.util.LinkedList;

public class HockeyAssistGoalStack<String>{
    
    private LinkedList<String> players;
    private int sizeList;
    
    public HockeyAssistGoalStack(){
        players = new LinkedList<String>();
        setSizeListLimit(3);
    }
   
    public void push(String hp) throws Exception{
        
        if(players.size() >= this.getSizeListLimit())
            players.removeFirst();
        
        if( players.contains(hp) ){
            players.removeLastOccurrence(hp);
            players.push(hp);
        }
   
        else players.push(hp);
        
    }
    public boolean isEmpty(){
        return players.isEmpty();
    }
    
    public boolean isFull(){
        return players.size() == this.getSizeListLimit();
    }
    
    public void clear(){
        players.clear();
    }
    public String getLast() throws HockeyListEmptyException{
        if(players.size() == 0){
            throw new HockeyListEmptyException();
        }
        else return players.getLast();
    }
    public String pop() throws HockeyListEmptyException{
        if(getSize() == 0)
            throw new HockeyListEmptyException();
         
        else return players.pop();
    }
    public int getSize(){
        return players.size();
    }
    public void setSizeListLimit(int size){
        this.sizeList = size;
    }
    
    public int getSizeListLimit(){
        return sizeList;
    }
}
