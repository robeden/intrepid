package com.starlight.intrepid;

import javax.annotation.Nonnull;
import java.io.*;

import static java.util.Objects.requireNonNull;

/**
 * Simple implementation of ObjectCodec using Java Serialization (ObjectInput/Output).
 */
class JavaSerializationObjectCodec implements ObjectCodec {
    @Override
    public void writeObject(@Nonnull Object object, @Nonnull OutputStream out) throws IOException {
        requireNonNull(object);

        try (ObjectOutputStream oout = new ObjectOutputStream(out)) {
            oout.writeUnshared(object);
        }
    }

    @Override
    public Object readObject(@Nonnull InputStream in) throws ClassNotFoundException, IOException {
        try (ObjectInputStream oin = new ObjectInputStream(in)) {
            return oin.readObject();
        }
    }
}
