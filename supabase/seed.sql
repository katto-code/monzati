-- Monzatti · catálogo de demostración: marcas ficticias administradas por Monzatti (owner_id null).
-- Precios en el rango de las marcas mexicanas analizadas ($1,500 a $5,000 MXN). Se puede ejecutar una sola vez.

with marcas as (
  insert into public.brands (name, verified)
  values ('Francesa', true), ('Nómada', true), ('Raíz', true), ('Taller Norte', true),
         ('Cobalto', true), ('Luna Roja', true), ('Urbe', true)
  returning id, name
),
productos as (
  insert into public.products (brand_id, name, category, color, emoji, price_mxn)
  select m.id, p.name, p.category, p.color, p.emoji, p.price
    from marcas m
    join (values
      ('Francesa', 'Blusa de seda', 'Blusas', 'Rosa pastel', '👚', 2290),
      ('Francesa', 'Blusa con moño', 'Blusas', 'Marfil', '👚', 1690),
      ('Nómada', 'Vestido midi', 'Vestidos', 'Vino', '👗', 3490),
      ('Raíz', 'Pantalón sastre', 'Pantalones', 'Negro', '👖', 1890),
      ('Taller Norte', 'Blazer camel', 'Chamarras', 'Camel', '🧥', 4290),
      ('Cobalto', 'Chamarra bomber', 'Chamarras', 'Verde oliva', '🧥', 2890),
      ('Luna Roja', 'Vestido de encaje', 'Vestidos', 'Negro', '👗', 3890),
      ('Urbe', 'Playera oversize', 'Blusas', 'Naranja', '👕', 1590)
    ) as p (brand, name, category, color, emoji, price) on p.brand = m.name
  returning id, name
)
insert into public.product_variants (product_id, size, stock)
select pr.id, v.size, v.stock
  from productos pr
  join (values
    ('Blusa de seda', 'XS', 2), ('Blusa de seda', 'S', 5), ('Blusa de seda', 'M', 6), ('Blusa de seda', 'L', 4), ('Blusa de seda', 'XL', 1),
    ('Blusa con moño', 'S', 4), ('Blusa con moño', 'M', 4), ('Blusa con moño', 'L', 3),
    ('Vestido midi', 'XS', 0), ('Vestido midi', 'S', 1), ('Vestido midi', 'M', 2), ('Vestido midi', 'L', 0),
    ('Pantalón sastre', 'S', 0), ('Pantalón sastre', 'M', 0), ('Pantalón sastre', 'L', 0),
    ('Blazer camel', 'S', 3), ('Blazer camel', 'M', 4), ('Blazer camel', 'L', 2),
    ('Chamarra bomber', 'S', 2), ('Chamarra bomber', 'M', 5), ('Chamarra bomber', 'L', 5), ('Chamarra bomber', 'XL', 2),
    ('Vestido de encaje', 'XS', 1), ('Vestido de encaje', 'S', 2), ('Vestido de encaje', 'M', 2),
    ('Playera oversize', 'M', 6), ('Playera oversize', 'L', 6), ('Playera oversize', 'XL', 4)
  ) as v (product, size, stock) on v.product = pr.name;
