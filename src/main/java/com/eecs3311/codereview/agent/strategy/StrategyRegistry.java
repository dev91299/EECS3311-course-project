package com.eecs3311.codereview.agent.strategy;

import com.eecs3311.codereview.repo.StandardsChecker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The catalogue of available review strategies.
 *
 * <p>Both interfaces and the autonomous agent resolve a strategy by name through this one
 * place, so the list of analyses offered in the GUI dropdown, in the CLI {@code analyze}
 * command and in the agent's {@code analyze_file} tool can never drift apart.</p>
 */
public class StrategyRegistry {

    private final Map<String, ReviewStrategy> strategies = new LinkedHashMap<>();

    /**
     * Builds the registry with the four built-in strategies.
     *
     * @param checker rule engine handed to the hybrid style strategy
     */
    public StrategyRegistry(StandardsChecker checker) {
        register(new SecurityStrategy());
        register(new StyleStrategy(checker));
        register(new PerformanceStrategy());
        register(new MaintainabilityStrategy());
    }

    /** Adds a strategy, replacing any existing one with the same name. */
    public final void register(ReviewStrategy strategy) {
        if (strategy != null) {
            strategies.put(strategy.getName().toLowerCase(), strategy);
        }
    }

    /** Looks a strategy up by name, case-insensitively. */
    public Optional<ReviewStrategy> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(strategies.get(name.trim().toLowerCase()));
    }

    /** The default strategy, used when the user does not name one. */
    public ReviewStrategy getDefault() {
        return strategies.get("security");
    }

    /** Every registered strategy, in registration order. */
    public List<ReviewStrategy> all() {
        return new ArrayList<>(strategies.values());
    }

    /** Strategy names, for the GUI dropdown and CLI help text. */
    public List<String> names() {
        return new ArrayList<>(strategies.keySet());
    }

    /** A formatted list of names and descriptions for the CLI {@code help} output. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (ReviewStrategy strategy : strategies.values()) {
            sb.append(String.format("  %-16s %s%n", strategy.getName(), strategy.getDescription()));
        }
        return sb.toString();
    }
}
