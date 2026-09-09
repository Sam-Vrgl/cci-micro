package com.formation.fitclass.repository;

import com.formation.fitclass.dto.ClassSearchCriteria;
import com.formation.fitclass.model.FitnessClass;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Traduit les filtres de recherche en predicats JPA. Chaque filtre nul est simplement ignore,
 * ce qui permet de combiner librement categorie, niveau, lieu, instructeur et fenetre de dates.
 */
public final class FitnessClassSpecifications {

    private FitnessClassSpecifications() {}

    public static Specification<FitnessClass> from(ClassSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.getCategory() != null) {
                predicates.add(builder.equal(root.get("category"), criteria.getCategory()));
            }
            if (criteria.getLevel() != null) {
                predicates.add(builder.equal(root.get("level"), criteria.getLevel()));
            }
            if (criteria.getStatus() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.getStatus()));
            }
            if (hasText(criteria.getLocation())) {
                predicates.add(likeIgnoreCase(builder, root.get("gymLocation"), criteria.getLocation()));
            }
            if (hasText(criteria.getInstructor())) {
                predicates.add(likeIgnoreCase(builder, root.get("instructor"), criteria.getInstructor()));
            }
            if (criteria.getDateFrom() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("dateTime"),
                        criteria.getDateFrom().atStartOfDay()));
            }
            if (criteria.getDateTo() != null) {
                // Borne haute exclusive au lendemain 00:00, pour inclure toute la journee dateTo
                LocalDateTime endOfDay = criteria.getDateTo().plusDays(1).atStartOfDay();
                predicates.add(builder.lessThan(root.get("dateTime"), endOfDay));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate likeIgnoreCase(CriteriaBuilder builder, Path<String> path, String value) {
        return builder.like(builder.lower(path), "%" + value.toLowerCase() + "%");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
