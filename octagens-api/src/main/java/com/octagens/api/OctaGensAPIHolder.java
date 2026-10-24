package com.octagens.api;

/** Package-private holder for the registered API instance. */
final class OctaGensAPIHolder {
    static volatile OctaGensAPI instance;

    private OctaGensAPIHolder() {
    }
}
