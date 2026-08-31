-- ============================================================================
-- O nucleo do projeto: impedir que duas reservas ocupem a mesma quadra no mesmo
-- intervalo, mesmo sob concorrencia.
--
-- Por que nao validar em Java com um SELECT antes do INSERT:
--   Entre a consulta e a gravacao existe uma janela. Duas transacoes simultaneas
--   consultam, ambas veem o horario livre, e ambas inserem. O SELECT so ve o que
--   ja foi commitado — a reserva concorrente ainda nao commitada e invisivel.
--
-- Por que nao lock otimista (@Version):
--   Ele detecta duas escritas na MESMA linha. Aqui o conflito e entre linhas
--   DIFERENTES cujos intervalos se cruzam. Nao ha versao em comum para comparar.
--
-- Por que nao lock pessimista na quadra (SELECT ... FOR UPDATE):
--   Funciona, mas serializa TODAS as reservas daquela quadra. Duas pessoas
--   reservando as 9h e as 15h nao conflitam entre si e mesmo assim ficariam
--   em fila.
--
-- A constraint de exclusao coloca a garantia no unico ponto sem janela de corrida
-- (o proprio banco, no momento do INSERT) e ainda permite inserts concorrentes
-- que nao colidem.
-- ============================================================================

-- Um indice GiST nao sabe, por padrao, comparar bigint com "=". A extensao
-- btree_gist ensina isso, e e o que permite combinar quadra_id (igualdade) com
-- o intervalo de tempo (sobreposicao) no mesmo indice.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reservas
    ADD CONSTRAINT reservas_sem_sobreposicao
    EXCLUDE USING gist (
        quadra_id WITH =,
        -- '[)' = intervalo semiaberto: inclui o inicio, exclui o fim. E o que faz
        -- 09:00-10:00 e 10:00-11:00 NAO conflitarem, que e o comportamento
        -- esperado de uma agenda. Com '[]' elas colidiriam no instante das 10:00.
        tstzrange(inicio, fim, '[)') WITH &&
    )
    -- Reserva cancelada libera o horario. Sem este WHERE, cancelar uma reserva
    -- deixaria o horario bloqueado para sempre.
    WHERE (status <> 'CANCELADA');

COMMENT ON CONSTRAINT reservas_sem_sobreposicao ON reservas IS
    'Garante no banco que duas reservas ativas nao ocupem a mesma quadra em intervalos sobrepostos.';
