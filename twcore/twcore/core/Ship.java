/*
 * Ship.java
 *
 * Created on October 24, 2002, 10:11 AM
 *
 * @author  harvey
 */

package twcore.core;

public class Ship extends Thread {

    public static int       MOVING_TIME = 100;
    public static int       UNMOVING_TIME = 1000;
    public static boolean   MOVEMENT_MOVING = true;
    public static boolean   MOVEMENT_UNMOVING = false;

    private short       x = 8192;
    private short       y = 8192;
    private short       xVel = 0;
    private short       yVel = 0;
    private short       bounty = 3;
    private short       energy = 1500;
    private byte        togglables = 0;
    private byte        direction = 0x0;
    private boolean     m_moving = MOVEMENT_UNMOVING;

    private short lastXV = 0;
    private short lastYV = 0;
    private byte lastD = 0x0;

    private int shipType = 8;

    private int m_mAge = (int)System.currentTimeMillis();
    private int m_pAge = (int)System.currentTimeMillis();

    private GamePacketGenerator     m_gen;

    Ship( ThreadGroup group, GamePacketGenerator gen ){
        super( group, "Ship" );
        m_gen = gen;
    }

    public void move( int direction, int x, int y, int xVel, int yVel, int togglables, int energy, int bounty ){
        this.x = (short)x;
        this.y = (short)y;
        this.xVel = (short)xVel;
        this.yVel = (short)yVel;
        this.direction = (byte)direction;
        this.togglables = (byte)togglables;
        this.energy = (short)energy;
        this.bounty = (short)bounty;
        setMoving( true );
        sendPositionPacket();
    }

    public void move( int x, int y, int xVel, int yVel ){
        this.x = (short)x;
        this.y = (short)y;
        this.xVel = (short)xVel;
        this.yVel = (short)yVel;
        setMoving( true );
        sendPositionPacket();
    }

    public void move( int x, int y ){
        this.x = (short)x;
        this.y = (short)y;
        this.xVel = 0;
        this.yVel = 0;
        setMoving( false );
        sendPositionPacket();
    }

    public void setVelocitiesAndDir(int xVel, int yVel, int d)
    {
        this.xVel = (short)xVel;
        this.yVel = (short)yVel;
        direction = (byte)d;
    }

    public void setMoving( boolean moving ){
        m_moving = moving;
    }

    public void setRotation( int rotation ){
        direction = (byte)rotation;
        sendPositionPacket();
    }

    public void updatePosition()
    {
        if (xVel != 0 && yVel != 0)
        {
            x += (short)(xVel * getAge() / 10000.0);
            y += (short)(yVel * getAge() / 10000.0);
        }
        m_mAge = (int)System.currentTimeMillis();

        if (needsToBeSent())
        {
            sendPositionPacket();
            lastXV = xVel;
            lastYV = yVel;
            lastD = direction;
            m_pAge = (int)System.currentTimeMillis();
        }
    }

    public int getAge() {
        return (int)System.currentTimeMillis() - m_mAge;
    }

    public boolean needsToBeSent()
    {
        return xVel != lastXV || yVel != lastYV || lastD != direction || (int)System.currentTimeMillis() - m_pAge >= 1000;
    }

    public void sendPositionPacket()
    {
        m_gen.sendPositionPacket( direction, (short)xVel, (short)y, (byte)togglables, (short)x, (short)yVel, (short)bounty, (short)energy, (short)0 );
    }

    public void fire( int weapon ){
        m_gen.sendPositionPacket( direction, (short)xVel, (short)y, (byte)togglables, (short)x, (short)yVel, (short)bounty, (short)energy, (short)weapon );
    }

    public void run(){
        try {
            while( !interrupted() ){
                updatePosition();
                if ( shipType != 8 ){
                    Thread.sleep( MOVING_TIME );
                } else {
                    Thread.sleep( UNMOVING_TIME );
                }
            }
        } catch( InterruptedException e ){
            return;
        }
    }

    public void rotateRadians( double rads ){
        rotateDegrees( Math.round( (int)Math.toDegrees( rads )) );
    }

    public void rotateDegrees( int degrees ){
        int rotate = (Math.round( (float)degrees / (float)9 ) + 10) % 40;
        setRotation( rotate );
    }

    public void setShip( int shipType ){
        this.shipType = (short)shipType;
        m_gen.sendShipChangePacket( (short)shipType );
    }

    public void setFreq( int freq ){
        m_gen.sendFreqChangePacket( (short)freq );
    }

    public void attach( int playerId ){
        m_gen.sendAttachRequestPacket( (short)playerId );
    }

    public void unattach(){
        m_gen.sendAttachRequestPacket( (short)-1 );
    }
    
    /**
     * @return Returns the x.
     */
    public short getX(){
        return x;
    }
    /**
     * @return Returns the y.
     */
    public short getY(){
        return y;
    }
}
