package co.conciliacion.backend.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceJpaRepository extends JpaRepository<InvoiceEntity, String> {

    /** SELECT ... FOR UPDATE: quien llegue segundo espera a que termine la transacción del primero. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InvoiceEntity i where i.number = :number")
    Optional<InvoiceEntity> findByNumberForUpdate(@Param("number") String number);
}