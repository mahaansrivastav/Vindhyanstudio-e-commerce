package com.vindhyanstudio.catalogue;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, String> {

    Optional<Product> findBySlugAndActiveTrue(String slug);

    @Query("""
            select p from Product p
            where p.active = true
              and (:category is null or p.category.slug = :category)
              and (:q is null or lower(p.name) like :q or lower(p.description) like :q or lower(p.category.name) like :q)
            order by p.category.sortOrder, p.name
            """)
    List<Product> search(@Param("category") String category, @Param("q") String q);

    /** Conditional decrement so two orders can never oversell the last unit. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("update Product p set p.stock = p.stock - :qty where p.sku = :sku and p.stock >= :qty")
    int decrementStock(@Param("sku") String sku, @Param("qty") int qty);
}
