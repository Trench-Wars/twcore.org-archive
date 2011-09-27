package twcore.bots.twdhub;

import java.util.Vector;

/**
 *
 * @author WingZero
 */
public class VectorSet<V> extends Vector<V> {

    private static final long serialVersionUID = 1L;
    
    @Override
    public boolean add(V o) {
        if (super.contains(o))
            return false;
        else 
            super.add(o);
        return true;
    }
    
}
