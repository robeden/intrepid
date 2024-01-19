package com.starlight.intrepid;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Interface for controlling how objects are encoded on the wire. The {@link #DEFAULT}
 * implementation uses the {@link java.io.ObjectOutputStream Java Serialization Protocol},
 * but implementing this interface and
 * {@link Intrepid.Builder#objectCodec(ObjectCodec) configuring it on the Intrepid instance}
 * will change that behavior as desired.
 */
public interface ObjectCodec {
    /**
     * Default implementation using Java Serialization.
     */
    ObjectCodec DEFAULT = new JavaSerializationObjectCodec();

    /**
     * Write the provided object to the given stream. Only the object data need be written.
     * Length will be tracked externally.
     */
    void writeObject(@Nonnull Object object, @Nonnull OutputStream out) throws IOException;

    /**
     * Read an object from the given stream.
     */
    Object readObject(@Nonnull InputStream in) throws ClassNotFoundException, IOException;
}
