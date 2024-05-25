package net.neoforged.fml.common.asm.enumextension;

import java.util.List;
import org.objectweb.asm.Type;

public interface EnumParameters {
    record Constant(List<Object> params) implements EnumParameters {}

    record ListBased(Type owner, String fieldName) implements EnumParameters {}
}
