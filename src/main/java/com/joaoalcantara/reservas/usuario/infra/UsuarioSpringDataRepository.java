package com.joaoalcantara.reservas.usuario.infra;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.joaoalcantara.reservas.usuario.dominio.Usuario;

interface UsuarioSpringDataRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
