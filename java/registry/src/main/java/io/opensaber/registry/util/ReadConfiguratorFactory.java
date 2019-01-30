package io.opensaber.registry.util;

public class ReadConfiguratorFactory {
    public static ReadConfigurator getDefault() {
        ReadConfigurator configurator = new ReadConfigurator();
        configurator.setIncludeSignatures(false);
        configurator.setIncludeTypeAttributes(false);
        return configurator;
    }

    public static ReadConfigurator getWithSignatures() {
        ReadConfigurator configurator = new ReadConfigurator();
        configurator.setIncludeSignatures(false);
        configurator.setIncludeTypeAttributes(false);
        return configurator;
    }

    public static ReadConfigurator getOne(boolean withSignatures) {
        if (withSignatures) {
            return getWithSignatures();
        } else {
            return  getDefault();
        }
    }

    public static ReadConfigurator getForUpdateValidation() {
        ReadConfigurator configurator = new ReadConfigurator();
        // For update, there could be signatures required.
        configurator.setIncludeSignatures(true);

        // Get rid of type attributes, which would fail validation
        configurator.setIncludeTypeAttributes(false);

        // Get rid of uuidPropertyNames, which would fail validation
        configurator.setIncludeIdentifiers(false);

        return configurator;
    }

}
