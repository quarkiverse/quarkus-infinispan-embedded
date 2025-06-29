package io.quarkiverse.infinispan.embedded.it.extension;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Date;
import java.util.Set;

import org.infinispan.commons.marshall.AbstractExternalizer;
import org.infinispan.commons.marshall.AdvancedExternalizer;
import org.infinispan.commons.util.Util;

/**
 * Contains various AdvancedExternalizers implementations to test with XML configuration
 */
public class TestAdvancedExternalizer {

    public static class IdViaConfigObj {
        String name;

        public IdViaConfigObj setName(String name) {
            this.name = name;
            return this;
        }

        public static class Externalizer implements AdvancedExternalizer<IdViaConfigObj> {
            @Override
            public void writeObject(ObjectOutput output, IdViaConfigObj object) throws IOException {
                output.writeUTF(object.name);
            }

            @Override
            public IdViaConfigObj readObject(ObjectInput input) throws IOException, ClassNotFoundException {
                return new IdViaConfigObj().setName(input.readUTF());
            }

            @Override
            public Set<Class<? extends IdViaConfigObj>> getTypeClasses() {
                return Util.asSet(IdViaConfigObj.class);
            }

            @Override
            public Integer getId() {
                return null;
            }
        }
    }

    public static class IdViaAnnotationObj {
        Date date;

        public IdViaAnnotationObj setDate(Date date) {
            this.date = date;
            return this;
        }

        public static class Externalizer extends AbstractExternalizer<IdViaAnnotationObj> {
            @Override
            public void writeObject(ObjectOutput output, IdViaAnnotationObj object) throws IOException {
                output.writeObject(object.date);
            }

            @Override
            public IdViaAnnotationObj readObject(ObjectInput input) throws IOException, ClassNotFoundException {
                return new IdViaAnnotationObj().setDate((Date) input.readObject());
            }

            @Override
            public Integer getId() {
                return 5678;
            }

            @Override
            public Set<Class<? extends IdViaAnnotationObj>> getTypeClasses() {
                return Util.asSet(IdViaAnnotationObj.class);
            }
        }
    }
}
