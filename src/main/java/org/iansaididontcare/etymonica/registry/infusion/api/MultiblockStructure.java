package org.iansaididontcare.etymonica.registry.infusion.api;

import java.util.List;

public record MultiblockStructure(
    int offsetY,
    List<List<String>> bottom,
    List<List<String>> middle,
    List<List<String>> top
) {
    public static final MultiblockStructure DEFAULT = new MultiblockStructure(
        3,
        List.of(
            List.of("etymonica:orichalcum_block", "etymonica:orichalcum_block", "etymonica:orichalcum_block"),
            List.of("etymonica:orichalcum_block", "etymonica:orichalcum_block", "etymonica:orichalcum_block"),
            List.of("etymonica:orichalcum_block", "etymonica:orichalcum_block", "etymonica:orichalcum_block")
        ),
        List.of(
            List.of("etymonica:orichalcum_block", "etymonica:orichalcum_block", "etymonica:orichalcum_block"),
            List.of("etymonica:orichalcum_block", "etymonica:orichalcum_block", "etymonica:orichalcum_block"),
            List.of("etymonica:orichalcum_block", "etymonica:orichalcum_block", "etymonica:orichalcum_block")
        ),
        List.of(
            List.of("etymonica:orichalcum_block", "etymonica:orichalcum_block", "etymonica:orichalcum_block"),
            List.of("etymonica:orichalcum_block", "etymonica:orichalcum_block", "etymonica:orichalcum_block"),
            List.of("etymonica:orichalcum_block", "etymonica:orichalcum_block", "etymonica:orichalcum_block")
        )
    );
}
