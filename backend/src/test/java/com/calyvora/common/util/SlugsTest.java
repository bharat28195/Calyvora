package com.calyvora.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

    @Test
    void slugifies_names() {
        assertThat(Slugs.slugify("Acme Inc.")).isEqualTo("acme-inc");
        assertThat(Slugs.slugify("  Héllo Wörld  ")).isEqualTo("hello-world");
        assertThat(Slugs.slugify("!!!")).isEqualTo("company");
        assertThat(Slugs.slugify("")).isEqualTo("company");
    }
}
