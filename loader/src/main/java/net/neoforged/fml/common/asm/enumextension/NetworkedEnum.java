package net.neoforged.fml.common.asm.enumextension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
public @interface NetworkedEnum {
    NetworkCheck value();

    enum NetworkCheck {
        /**
         * To be used for enums which are sent to the client, allowing connections to a vanilla server when the
         * enum is extended on the client but not to a vanilla client when the enum is extended on the server
         */
        CLIENTBOUND,
        /**
         * To be used for enums which are sent to the server, allowing connections to a vanilla client when the
         * enum is extended on the server but not to a vanilla server when the enum is extended on the client
         */
        SERVERBOUND,
        /**
         * To be used for enums which are sent in both directions, disallowing connections to either vanilla
         * counterpart when the enum is extended
         */
        BIDIRECTIONAL
    }
}
