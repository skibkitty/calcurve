package dev.skergan.calcurve.api;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import org.springframework.stereotype.Component;

/**
 * Rounds every {@code double} in JSON output to 6 significant figures. All math stays in full
 * double precision; rounding happens only at the serialization boundary.
 */
@Component
public class SignificantFiguresModule extends SimpleModule {

    static final MathContext SIX_SIG_FIGS = new MathContext(6);

    public SignificantFiguresModule() {
        super("SignificantFigures");
        addSerializer(Double.class, new SignificantFiguresSerializer());
        addSerializer(double.class, new SignificantFiguresSerializer());
    }

    static final class SignificantFiguresSerializer extends StdSerializer<Double> {

        SignificantFiguresSerializer() {
            super(Double.class);
        }

        @Override
        public void serialize(Double value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            if (!Double.isFinite(value)) {
                gen.writeNull();
                return;
            }
            BigDecimal rounded = new BigDecimal(value).round(SIX_SIG_FIGS).stripTrailingZeros();
            gen.writeNumber(rounded.toPlainString());
        }
    }
}
