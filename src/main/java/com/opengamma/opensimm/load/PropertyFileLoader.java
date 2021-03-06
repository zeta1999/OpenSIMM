/**
 * Copyright (C) 2015 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.opensimm.load;

import java.io.File;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import com.opengamma.opensimm.SimmCalculator;
import com.opengamma.opensimm.basics.AssetClass;
import com.opengamma.opensimm.basics.FxMatrix;
import com.opengamma.opensimm.basics.PortfolioExposure;
import com.opengamma.opensimm.basics.RiskFactor;
import com.opengamma.opensimm.basics.RiskFactorProperties;
import com.opengamma.opensimm.util.Pair;

/**
 * Reads a set of properties (generally from a file) and
 * locates the items required for the SIMM calculation.
 */
public class PropertyFileLoader {

  private static final String DEFAULT_VAR_LEVEL = "0.99";

  private static final String BASE_CURRENCY_KEY = "base-currency";
  private static final String VAR_LEVEL_KEY = "var-level";

  private static final String RISK_FACTOR_DEFINITIONS_KEY = "risk-factor-definitions";
  private static final String RISK_FACTOR_BASE_LEVELS_KEY = "risk-factor-base-levels";
  private static final String RISK_FACTOR_SHOCKS_KEY = "risk-factor-shocks";
  private static final String FX_RATES_KEY = "fx-rates";
  private static final String FX_RATE_SHOCKS_KEY = "fx-rate-shocks";
  private static final String DERIVATIVES_KEY = "portfolio-derivatives";
  private static final String INITIAL_MARGIN_KEY = "portfolio-initial-margin";
  private static final String VARIATION_MARGIN_KEY = "portfolio-variation-margin";

  private final double varLevel;
  private final Currency baseCurrency;
  private final File riskFactors;
  private final File riskFactorBaseLevels;
  private final File riskFactorShocks;
  private final File fxRates;
  private final File fxRateShocks;
  private final File derivatives;
  private final Optional<File> initialMargin;
  private final Optional<File> variationMargin;

  /**
   * Creates a reader for the supplied properties.
   *
   * @param props  the properties to be used
   * @throws RuntimeException if there is a problem parsing the files
   */
  public PropertyFileLoader(Properties props) {

    varLevel = Double.parseDouble(props.getProperty(VAR_LEVEL_KEY, DEFAULT_VAR_LEVEL));
    baseCurrency = Currency.getInstance(loadProperty(props, BASE_CURRENCY_KEY));
    riskFactors = locateFile(props, RISK_FACTOR_DEFINITIONS_KEY);
    riskFactorBaseLevels = locateFile(props, RISK_FACTOR_BASE_LEVELS_KEY);
    riskFactorShocks = locateFile(props, RISK_FACTOR_SHOCKS_KEY);
    fxRates = locateFile(props, FX_RATES_KEY);
    fxRateShocks = locateFile(props, FX_RATE_SHOCKS_KEY);
    derivatives = locateFile(props, DERIVATIVES_KEY);
    initialMargin = locateOptionalFile(props, INITIAL_MARGIN_KEY);
    variationMargin = locateOptionalFile(props, VARIATION_MARGIN_KEY);
  }

  /**
   * Create a new {@link SimmCalculator} using data from the files
   * defined in the properties.
   *
   * @return a new {@link SimmCalculator}
   */
  public SimmCalculator createSimmCalculator() {

    return SimmCalculator.builder()
        .varLevel(varLevel)
        .baseCurrency(baseCurrency)
        .riskFactors(loadRiskFactorDefinitions())
        .riskFactorLevels(loadRiskFactorLevels())
        .fxMatrix(loadFxMatrix())
        .riskFactorShocks(loadRiskFactorShocks())
        .fxShocks(loadFxShocks())
        .build();
  }

  /**
   * Create SIMM VaR per asset class using data from the files
   * defined in the properties.
   *
   * @return the VaR results
   */
  public Map<AssetClass, Double> calculateVar() {

    SimmCalculator calculator = createSimmCalculator();
    Set<RiskFactor> riskFactors = calculator.getRiskFactors();
    return calculator.varByAssetClass(
        loadDerivatives(riskFactors),
        loadInitialMargin(riskFactors),
        loadVariationMargin(riskFactors));
  }

  /**
   * Create the SIMM P&amp;L vectors per asset class using data
   * from the files defined in the properties.
   *
   * @return the P&amp;L vectors
   */
  public Map<AssetClass, List<Pair<Integer, Double>>> calculatePnlVectors() {

    SimmCalculator calculator = createSimmCalculator();
    Set<RiskFactor> riskFactors = calculator.getRiskFactors();
    return calculator.pnlVectorsByAssetClass(
        loadDerivatives(riskFactors),
        loadInitialMargin(riskFactors),
        loadVariationMargin(riskFactors));
  }

  private Optional<File> locateOptionalFile(Properties props, String key) {
    return loadOptionalProperty(props, key).map(File::new);
  }

  private File locateFile(Properties props, String key) {
    String fileName = loadProperty(props, key);
    File file = new File(fileName);
    if (!file.exists()) {
      throw new IllegalStateException("Could not find file: " + fileName + " defined for property: " + key);
    }
    return file;
  }

  private Optional<String> loadOptionalProperty(Properties props, String key) {
    String property = props.getProperty(key);
    return Optional.ofNullable(property);
  }

  private String loadProperty(Properties props, String key) {
    return loadOptionalProperty(props, key)
        .orElseThrow(() -> new IllegalStateException("No property found for: " + key));
  }

  private List<PortfolioExposure> loadVariationMargin(Set<RiskFactor> riskFactors) {
    return variationMargin
        .map(f -> PortfolioLoader.of(f, riskFactors).load())
        .orElse(new ArrayList<>());
  }

  private List<PortfolioExposure> loadInitialMargin(Set<RiskFactor> riskFactors) {
    return initialMargin
        .map(f -> PortfolioLoader.of(f, riskFactors).load())
        .orElse(new ArrayList<>());
  }

  private List<PortfolioExposure> loadDerivatives(Set<RiskFactor> riskFactors) {
    return PortfolioLoader.of(derivatives, riskFactors).load();
  }

  private Map<RiskFactor, RiskFactorProperties> loadRiskFactorDefinitions() {
    return RiskFactorDefinitionsLoader.of(riskFactors).load();
  }

  private Map<RiskFactor, Double> loadRiskFactorLevels() {
    return RiskFactorBaseLevelsLoader.of(riskFactorBaseLevels).load();
  }

  private FxMatrix loadFxMatrix() {
    return FxRateLoader.of(fxRates).load();
  }

  private Map<RiskFactor, List<Double>> loadRiskFactorShocks() {
    return RiskFactorShocksLoader.of(riskFactorShocks).load();
  }

  private Map<Pair<Currency, Currency>, List<Double>> loadFxShocks() {
    return FxShocksLoader.of(fxRateShocks).load();
  }
}
