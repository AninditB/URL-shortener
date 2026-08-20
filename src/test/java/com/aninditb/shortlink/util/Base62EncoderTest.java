package com.aninditb.shortlink.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Base62EncoderTest {

    @Test
    void encodesZeroAsFirstAlphabetCharacter() {
        assertThat(Base62Encoder.encode(0)).isEqualTo("0");
    }

    @Test
    void encodesSmallValues() {
        assertThat(Base62Encoder.encode(1)).isEqualTo("1");
        assertThat(Base62Encoder.encode(35)).isEqualTo("Z");
        assertThat(Base62Encoder.encode(61)).isEqualTo("z");
    }

    @Test
    void encodesValueRequiringTwoDigits() {
        assertThat(Base62Encoder.encode(62)).isEqualTo("10");
        assertThat(Base62Encoder.encode(3843)).isEqualTo("zz");
    }

    @Test
    void encodesLargeValueWithoutError() {
        String encoded = Base62Encoder.encode(Long.MAX_VALUE - 1);
        assertThat(encoded).isNotBlank();
        assertThat(encoded).matches("[0-9A-Za-z]+");
    }

    @Test
    void rejectsNegativeValues() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> Base62Encoder.encode(-1)
        );
    }
}
