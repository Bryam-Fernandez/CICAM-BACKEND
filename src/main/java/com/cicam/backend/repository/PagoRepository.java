package com.cicam.backend.repository;

import com.cicam.backend.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoRepository extends JpaRepository<Pago, Long> {
    boolean existsByReferencia(String referencia);
}
