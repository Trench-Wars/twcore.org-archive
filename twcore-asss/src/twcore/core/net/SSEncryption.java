package twcore.core.net;

import twcore.core.util.ByteArray;

/**
 * Subspace encryption class.
 *
 * For the vast majority of uses of TWCore, the understanding of this class is
 * completely unnecessary.  It can be ignored simply as a black box that encrypts
 * and decrypts packets as needed.
 *
 * A key is randomly generated by the client, which is sent to the server as a
 * special core packet (0x01), and the server returns a key based on this (in
 * core packet 0x02).  An encryption table is then generated from the server
 * key, which is applied with the server key to encrypt/decrypt packets.
 */
public class SSEncryption {

    private int m_serverKey;          // Key retrieved from the server
    @SuppressWarnings("unused")
    private int m_clientKey;          // Key randomly generated by the bot
    //ByteArray m_table = new ByteArray( 520 ); // Encryption/decryption table
    private int m_cryptTable[] = new int[130];

    /**
     * Creates a new instance of SSEncryption.
     */
    public SSEncryption(){

        m_serverKey = 0;
        m_clientKey = 0;
    }

    /**
     * Creates a new instance of SSEncryption using provided keys.
     * @param clientKey Key generated by client
     * @param serverKey Key generated by server
     */
    public SSEncryption(/*long*/ int clientKey, /*long*/ int serverKey){

        m_serverKey = serverKey;
        m_clientKey = clientKey;

        initialize( m_serverKey );
    }

    /**
     * Creates a new instance of SSEncryption using provided keys.
     * @param clientKey Key generated by client
     * @param serverKey Key generated by server
     */
    public SSEncryption( ByteArray clientKey, ByteArray serverKey ){

        m_serverKey = serverKey.readLittleEndianInt( 0 );
        m_clientKey = clientKey.readLittleEndianInt( 0 );

        initialize( m_serverKey );
    }

    /**
     * Explicitly sets the client key.
     * @param newClientKey Key generated by the client
     */
    public void setClientKey(/*long*/ int newClientKey) {

        m_clientKey = newClientKey;
    }

    /**
     * Explicitly sets the client key.
     * @param newClientKey Key generated by the client
     */
    public void setClientKey(ByteArray newClientKey) {

        m_clientKey = newClientKey.readLittleEndianInt( 0 );

        initialize( m_serverKey );
    }

    /**
     * Explicitly sets the server key.
     * @param newServerKey Key generated by the server
     */
    public void setServerKey(/*long*/ int newServerKey ){

        m_serverKey = newServerKey;

        initialize( m_serverKey );
    }

    /**
     * Explicitly sets the server key.
     * @param newServerKey Key generated by the server
     */
    public void setServerKey( ByteArray newServerKey ){

        m_serverKey = newServerKey.readLittleEndianInt( 0 );

        initialize( m_serverKey );
    }

    /**
     * Initializes the encryption table with the provided seed (should be seeded
     * with the server key).
     * @param seed
     */
    private void initialize(/*long*/ int seed) {
        long        oldSeed;
        long        tempSeed = ((long)seed) & 0x00000000FFFFFFFFL;

        //m_table.setPointerIndex( 0 );
        for( int i = 0; i < (520 / 2); i++ ){
            oldSeed = tempSeed;

            tempSeed = ((oldSeed * 0x834E0B5FL) >> 48) & 0xffffffffL;
            tempSeed = ((tempSeed + (tempSeed >> 31)) & 0xffffffffL);
            tempSeed = ((((oldSeed % 0x1F31DL) * 16807) - (tempSeed * 2836) + 123) & 0xffffffffL);
            if( tempSeed > 0x7fffffffL ){
                tempSeed = ((tempSeed + 0x7fffffffL) & 0xffffffffL);
            }

            //m_table.addLittleEndianShort( (short)(tempSeed & 0xffff) );

            m_cryptTable[i/2] = (i % 2 == 0) ? (int)tempSeed & 0xFFFF : m_cryptTable[i/2] | (int)tempSeed << 16;
        }
    }

    /**
     * Encrypts a ByteArray using the SS Encryption protocol, starting at index 0
     * and continuing for the specified length.
     * @param data ByteArray to encrypt
     * @param length Length of encryption
     */
    public void encrypt( ByteArray data, int length ){

        encrypt( data, length, 0 );
    }

    /**
     * Encrypts a ByteArray using the SS Encryption protocol, starting at the
     * specified index and continuing for the specified length.
     * @param data ByteArray to encrypt
     * @param length Length of encryption
     * @param index Index to begin encryption
     */
    public void encrypt( ByteArray data, int length, int index ){
        /*
        long           tempInt;
        long           tempKey = m_serverKey;
        int            count = data.size() + (4 - data.size()%4);
        ByteArray      output = new ByteArray( count );

        output.addPartialByteArray( data, 0, index, length );
        for( int i=0; i<count; i+=4 ){
            tempInt = output.readLittleEndianInt( i ) ^ m_table.readLittleEndianInt( i ) ^ tempKey;
            tempKey = tempInt;
            output.addLittleEndianInt( (int)(tempInt & 0xffffffff), i );
        }

        data.addPartialByteArray( output, index, 0, length );
        */

        int tempInt = m_serverKey;
        int count = index + length - 4;
        int bump = length & 3;
        int t = 0;

        for(; index <= count; index += 4) {
            tempInt = data.readLittleEndianInt(index) ^ m_cryptTable[t++] ^ tempInt;
            data.addLittleEndianInt(tempInt, index);
        }

        if(bump == 3) {
            tempInt = (((int)data.readLittleEndianShort(index) & 0xFFFF) | ((int)data.readByte(index + 2) << 16)) ^ m_cryptTable[t] ^ tempInt;
            data.addLittleEndianShort((short)tempInt, index);
            data.addByte((byte)(tempInt >> 16), index + 2);
            return;
        } else if(bump == 2) {
            data.addLittleEndianShort((short)(data.readLittleEndianShort(index) ^ m_cryptTable[t] ^ tempInt), index);
            return;
        } else if(bump == 1) {
            data.addByte((byte)(data.readByte(index) ^ m_cryptTable[t] ^ tempInt), index);
            return;
        }
    }

    /**
     * Decrypts a ByteArray using the SS Encryption protocol, starting at index 0
     * and continuing for the specified length.
     * @param data ByteArray to decrypt
     * @param length Length of decryption
     */
    public void decrypt( ByteArray data, int length ){

        decrypt( data, length, 0 );
    }

    /**
     * Decrypts a ByteArray using the SS Encryption protocol, starting at the
     * specified index and continuing for the specified length.
     * @param data ByteArray to decrypt
     * @param length Length of decryption
     * @param index Index to begin decryption
     */
    public void decrypt( ByteArray data, int length, int index ){
        /*
        long           tempInt;
        long           tempKey = m_serverKey;
        int            count = data.size() + (4 - data.size()%4);

        ByteArray      output = new ByteArray( count );

        output.addPartialByteArray( data, 0, index, length );
        for( int i = 0; i < count; i += 4 ){
            tempInt = m_table.readLittleEndianInt( i ) ^ tempKey ^ output.readLittleEndianInt( i );
            tempKey = output.readLittleEndianInt( i );
            output.addLittleEndianInt( (int)(tempInt & 0xffffffff), i );
        }
        data.addPartialByteArray( output, index, 0, length );
        */

        int tempInt;
        int tempKey = m_serverKey;
        int count = index + length - 4;
        int bump = length & 3;
        int t = 0;

        for(; index <= count; index += 4) {
            int work = data.readLittleEndianInt(index);
            tempInt = m_cryptTable[t++] ^ tempKey ^ work;
            tempKey = work;
            data.addLittleEndianInt(tempInt, index);
        }

        if(bump == 3) {
            tempInt = m_cryptTable[t] ^ tempKey ^ (((int)data.readLittleEndianShort(index) & 0xFFFF) | ((int)data.readByte(index + 2) << 16));
            data.addLittleEndianShort((short)tempInt, index);
            data.addByte((byte)(tempInt >> 16), index + 2);
            return;
        } else if(bump == 2) {
            data.addLittleEndianShort((short)(m_cryptTable[t] ^ tempKey ^ data.readLittleEndianShort(index)), index);
            return;
        } else if(bump == 1) {
            data.addByte((byte)(m_cryptTable[t] ^ tempKey ^ data.readByte(index)), index);
            return;
        }
    }
}
