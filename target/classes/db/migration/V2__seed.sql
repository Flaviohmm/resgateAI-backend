-- Catálogo: 11 produtos cobrindo as 6 categorias e os dois casos transformáveis (banana, tomate, pão)
INSERT INTO produto (nome, categoria, custo_unit, preco_unit, validade_dias, transformavel, transform_destino) VALUES
('Iogurte Morango 170g',     'LATICINIO',  2.10,  3.49,  21,  FALSE, NULL),
('Leite Integral 1L',        'LATICINIO',  3.80,  5.49,  10,  FALSE, NULL),
('Queijo Mussarela 400g',    'LATICINIO', 14.00, 22.90,  30,  FALSE, NULL),
('Banana Prata kg',          'HORTIFRUTI', 2.50,  4.99,   7,  TRUE,  'Bolo de banana'),
('Tomate kg',                'HORTIFRUTI', 3.20,  6.49,   6,  TRUE,  'Sugo de tomate'),
('Alface Crespa unid',       'HORTIFRUTI', 1.00,  2.49,   4,  FALSE, NULL),
('Pão de Forma 500g',        'PADARIA',    4.50,  7.99,   8,  TRUE,  'Farofa de pão'),
('Arroz Tipo 1 5kg',         'MERCEARIA', 18.00, 24.90, 365,  FALSE, NULL),
('Macarrão Espaguete 500g',  'MERCEARIA',  3.00,  4.99, 540,  FALSE, NULL),
('Linguiça Toscana kg',      'CARNES',    12.00, 19.90,  12,  FALSE, NULL),
('Refrigerante Cola 2L',     'BEBIDAS',    5.50,  8.99, 180,  FALSE, NULL);

-- Lotes em estoque: 18 lotes cobrindo as 5 rotas de resgate (Remarcar, Combinar, Reposicionar, Transformar, Doar)
INSERT INTO lote (produto_id, quantidade, data_recebido, data_validade, local_gondola) VALUES
((SELECT id FROM produto WHERE nome = 'Iogurte Morango 170g'),    40, CURRENT_DATE - 5, CURRENT_DATE + 2,  'Refrigerado A3'),
((SELECT id FROM produto WHERE nome = 'Iogurte Morango 170g'),    15, CURRENT_DATE - 3, CURRENT_DATE + 6,  'Refrigerado A3'),
((SELECT id FROM produto WHERE nome = 'Iogurte Morango 170g'),    10, CURRENT_DATE - 1, CURRENT_DATE + 20, 'Refrigerado A3'),
((SELECT id FROM produto WHERE nome = 'Leite Integral 1L'),       25, CURRENT_DATE - 8, CURRENT_DATE + 1,  'Refrigerado A1'),
((SELECT id FROM produto WHERE nome = 'Queijo Mussarela 400g'),    8, CURRENT_DATE - 10, CURRENT_DATE + 20, 'Refrigerado A2'),
((SELECT id FROM produto WHERE nome = 'Queijo Mussarela 400g'),    5, CURRENT_DATE - 25, CURRENT_DATE + 3,  'Refrigerado A2'),
((SELECT id FROM produto WHERE nome = 'Banana Prata kg'),         30, CURRENT_DATE - 4, CURRENT_DATE + 2,  'Hortifruti B1'),
((SELECT id FROM produto WHERE nome = 'Banana Prata kg'),         12, CURRENT_DATE - 2, CURRENT_DATE + 5,  'Hortifruti B1'),
((SELECT id FROM produto WHERE nome = 'Banana Prata kg'),          4, CURRENT_DATE,      CURRENT_DATE + 6,  'Hortifruti B1'),
((SELECT id FROM produto WHERE nome = 'Tomate kg'),                20, CURRENT_DATE - 3, CURRENT_DATE + 3,  'Hortifruti B2'),
((SELECT id FROM produto WHERE nome = 'Tomate kg'),                 6, CURRENT_DATE - 1, CURRENT_DATE + 1,  'Hortifruti B2'),
((SELECT id FROM produto WHERE nome = 'Alface Crespa unid'),       18, CURRENT_DATE - 2, CURRENT_DATE + 1,  'Hortifruti B3'),
((SELECT id FROM produto WHERE nome = 'Alface Crespa unid'),        5, CURRENT_DATE - 1, CURRENT_DATE,      'Hortifruti B3'),
((SELECT id FROM produto WHERE nome = 'Pão de Forma 500g'),        22, CURRENT_DATE - 5, CURRENT_DATE + 3,  'Padaria C1'),
((SELECT id FROM produto WHERE nome = 'Arroz Tipo 1 5kg'),         50, CURRENT_DATE - 60, CURRENT_DATE + 300, 'Mercearia D1'),
((SELECT id FROM produto WHERE nome = 'Macarrão Espaguete 500g'),  60, CURRENT_DATE - 90, CURRENT_DATE + 120, 'Mercearia D2'),
((SELECT id FROM produto WHERE nome = 'Linguiça Toscana kg'),      14, CURRENT_DATE - 6, CURRENT_DATE + 4,  'Refrigerado A4'),
((SELECT id FROM produto WHERE nome = 'Refrigerante Cola 2L'),     40, CURRENT_DATE - 100, CURRENT_DATE + 40, 'Bebidas E1');

-- Combo natural: Queijo Mussarela + Pão de Forma (par classico de sanduiche), usado pela rota COMBINAR.
-- Sem isso a regra "existe complemento parado no estoque" (Arquitetura §3.2) não tem dado para avaliar.
UPDATE produto SET combo_natural_id = (SELECT id FROM produto WHERE nome = 'Pão de Forma 500g')
WHERE nome = 'Queijo Mussarela 400g';

-- Sinal de giro/ocupação para os candidatos a REPOSICIONAR (giro baixo + nivel MEDIO,
-- garantido pelas datas de validade acima: dias_para_vencer cai dentro de validade_dias*0.3)
INSERT INTO volume (produto_id, lote_id, quantidade_atual, capacidade_prateleira, dias_na_prateleira, percentual_ocupacao, giro_estoque) VALUES
((SELECT id FROM produto WHERE nome = 'Macarrão Espaguete 500g'),
 (SELECT id FROM lote WHERE produto_id = (SELECT id FROM produto WHERE nome = 'Macarrão Espaguete 500g') ORDER BY id LIMIT 1),
 60, 80, 45, 75.00, 0.80),
((SELECT id FROM produto WHERE nome = 'Refrigerante Cola 2L'),
 (SELECT id FROM lote WHERE produto_id = (SELECT id FROM produto WHERE nome = 'Refrigerante Cola 2L') ORDER BY id LIMIT 1),
 40, 60, 60, 66.67, 0.50);

-- Histórico de resgate (semanas COMPLETAS anteriores, lotes já liquidados -> lote_id nulo) para alimentar o loop de aprendizado.
-- A semana corrente é gravada quando a fila é aberta na demo, mas é ignorada na contagem (semanas completas, Story 7.1),
-- então estes valores ficam estáveis em -30%/-20% por mais que a fila seja aberta.
-- Iogurte Morango: 3 das últimas 4 semanas -> ajuste_pct esperado = -(3/4)*40 = -30%, igual ao exemplo do PRD/Arquitetura.
INSERT INTO historico_resgate (produto_id, quantidade_em_risco, rota_aplicada, semana_ref) VALUES
((SELECT id FROM produto WHERE nome = 'Iogurte Morango 170g'), 32, 'REMARCAR', CURRENT_DATE - 7),
((SELECT id FROM produto WHERE nome = 'Iogurte Morango 170g'), 28, 'REMARCAR', CURRENT_DATE - 14),
((SELECT id FROM produto WHERE nome = 'Iogurte Morango 170g'), 36, 'REMARCAR', CURRENT_DATE - 21);

-- Pão de Forma: 2 das últimas 4 semanas -> ajuste_pct esperado = -(2/4)*40 = -20%.
INSERT INTO historico_resgate (produto_id, quantidade_em_risco, rota_aplicada, semana_ref) VALUES
((SELECT id FROM produto WHERE nome = 'Pão de Forma 500g'), 18, 'TRANSFORMAR', CURRENT_DATE - 7),
((SELECT id FROM produto WHERE nome = 'Pão de Forma 500g'), 15, 'TRANSFORMAR', CURRENT_DATE - 21);
