package edu.alibaba.mpc4j.psu.audit;

import edu.alibaba.mpc4j.common.rpc.pto.MultiPartyPtoConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.base.BaseOtConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.lnot.LnotConfig;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Walks a {@link MultiPartyPtoConfig} tree and detects OT/COT-related sub-config types.
 */
public final class OtConfigInspector {
    private OtConfigInspector() {
        // empty
    }

    public enum OtKind {
        NONE,
        CORE_COT,
        BASE_OT,
        LNOT,
        OTHER_OT
    }

    public static final class OtConfigScan {
        public final OtKind kind;
        public final boolean usesCoreCot;
        public final boolean usesBaseOt;
        public final boolean usesLnot;
        public final List<String> configTypeNames;

        OtConfigScan(OtKind kind, boolean usesCoreCot, boolean usesBaseOt, boolean usesLnot, List<String> configTypeNames) {
            this.kind = kind;
            this.usesCoreCot = usesCoreCot;
            this.usesBaseOt = usesBaseOt;
            this.usesLnot = usesLnot;
            this.configTypeNames = configTypeNames;
        }
    }

    public static OtConfigScan scan(MultiPartyPtoConfig root) {
        Set<Integer> visited = new HashSet<>();
        List<String> otNames = new ArrayList<>();
        boolean coreCot = false;
        boolean baseOt = false;
        boolean lnot = false;
        Deque<Object> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Object node = queue.removeFirst();
            if (node == null) {
                continue;
            }
            int id = System.identityHashCode(node);
            if (!visited.add(id)) {
                continue;
            }
            Class<?> clazz = node.getClass();
            if (CoreCotConfig.class.isAssignableFrom(clazz)) {
                coreCot = true;
                otNames.add(clazz.getSimpleName());
            }
            if (BaseOtConfig.class.isAssignableFrom(clazz)) {
                baseOt = true;
                otNames.add(clazz.getSimpleName());
            }
            if (LnotConfig.class.isAssignableFrom(clazz)) {
                lnot = true;
                otNames.add(clazz.getSimpleName());
            }
            if (isOtherOtConfig(clazz)) {
                otNames.add(clazz.getSimpleName());
            }
            if (node instanceof MultiPartyPtoConfig) {
                enqueueSubConfigs((MultiPartyPtoConfig) node, queue);
            }
            enqueueFields(node, queue, visited);
        }
        OtKind kind = OtKind.NONE;
        if (coreCot || baseOt || lnot || !otNames.isEmpty()) {
            if (lnot && !coreCot) {
                kind = OtKind.LNOT;
            } else if (coreCot) {
                kind = OtKind.CORE_COT;
            } else if (baseOt) {
                kind = OtKind.BASE_OT;
            } else {
                kind = OtKind.OTHER_OT;
            }
        }
        return new OtConfigScan(kind, coreCot, baseOt, lnot, otNames);
    }

    private static boolean isOtherOtConfig(Class<?> clazz) {
        String n = clazz.getName();
        return n.contains(".pcg.ot.") && !MultiPartyPtoConfig.class.isAssignableFrom(clazz)
            && !clazz.isInterface();
    }

    private static void enqueueSubConfigs(MultiPartyPtoConfig config, Deque<Object> queue) {
        try {
            Field f = findSubPtoConfigsField(config.getClass());
            if (f == null) {
                return;
            }
            f.setAccessible(true);
            Object arr = f.get(config);
            if (arr == null) {
                return;
            }
            int len = Array.getLength(arr);
            for (int i = 0; i < len; i++) {
                queue.addLast(Array.get(arr, i));
            }
        } catch (ReflectiveOperationException ignored) {
            // best-effort walk
        }
    }

    private static Field findSubPtoConfigsField(Class<?> type) {
        Class<?> c = type;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField("subPtoConfigs");
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static void enqueueFields(Object node, Deque<Object> queue, Set<Integer> visited) {
        Class<?> c = node.getClass();
        while (c != null && c != Object.class) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                Class<?> ft = field.getType();
                if (ft.isPrimitive() || CharSequence.class.isAssignableFrom(ft) || Number.class.isAssignableFrom(ft)) {
                    continue;
                }
                if (!MultiPartyPtoConfig.class.isAssignableFrom(ft) && !ft.getName().contains("Config")) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object val = field.get(node);
                    if (val == null) {
                        continue;
                    }
                    int id = System.identityHashCode(val);
                    if (!visited.contains(id)) {
                        queue.addLast(val);
                    }
                } catch (ReflectiveOperationException ignored) {
                    // skip
                }
            }
            c = c.getSuperclass();
        }
    }
}
