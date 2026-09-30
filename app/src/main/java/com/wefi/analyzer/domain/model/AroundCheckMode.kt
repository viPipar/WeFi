package com.wefi.analyzer.domain.model

enum class AroundCheckMode {
    BFS,    // 1 Password -> Multi Router
    DFS,    // Multi Password -> 1 Router
    HYBRID  // Multi Password -> Multi Router (DFS + BFS)
}
