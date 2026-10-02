package dev.arepa.requests.infrastructure;

import dev.arepa.requests.application.UnitOfWork;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

public final class SpringUnitOfWork implements UnitOfWork {
    private final TransactionTemplate transactions;
    public SpringUnitOfWork(TransactionTemplate transactions) { this.transactions = transactions; }
    public <T> T execute(Supplier<T> action) { return transactions.execute(status -> action.get()); }
}
