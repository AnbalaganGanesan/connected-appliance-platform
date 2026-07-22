package com.example.connectedappliance.shared.error;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jakarta.validation.ConstraintViolation;

import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

@Component
public class ValidationErrorMapper {

    private static final Comparator<ValidationItem> ITEM_ORDER = Comparator
            .comparing(ValidationItem::field)
            .thenComparing(ValidationItem::code)
            .thenComparing(ValidationItem::message);

    private final ValidationCodeMapper codeMapper;

    public ValidationErrorMapper(ValidationCodeMapper codeMapper) {
        this.codeMapper = codeMapper;
    }

    public List<ValidationItem> fromBindingResult(BindingResult bindingResult) {
        List<ValidationItem> items = new ArrayList<>();
        for (ObjectError error : bindingResult.getAllErrors()) {
            String field = error instanceof FieldError fieldError
                    ? fieldError.getField()
                    : "request";
            String publicCode = codeMapper.map(error.getCode());
            items.add(new ValidationItem(field, publicCode, codeMapper.message(publicCode)));
        }
        return sorted(items);
    }

    public List<ValidationItem> fromConstraintViolations(
            Iterable<ConstraintViolation<?>> violations) {
        List<ValidationItem> items = new ArrayList<>();
        for (ConstraintViolation<?> violation : violations) {
            String field = publicPathLeaf(violation.getPropertyPath().toString());
            String annotation = violation.getConstraintDescriptor()
                    .getAnnotation()
                    .annotationType()
                    .getSimpleName();
            String publicCode = codeMapper.map(annotation);
            items.add(new ValidationItem(field, publicCode, codeMapper.message(publicCode)));
        }
        return sorted(items);
    }

    public List<ValidationItem> single(String field, String code) {
        return List.of(new ValidationItem(field, code, codeMapper.message(code)));
    }

    public List<ValidationItem> sorted(List<ValidationItem> items) {
        return items.stream().sorted(ITEM_ORDER).toList();
    }

    private String publicPathLeaf(String path) {
        int separator = path.lastIndexOf('.');
        String leaf = separator >= 0 ? path.substring(separator + 1) : path;
        if (leaf.isBlank() || leaf.startsWith("arg")) {
            return "request";
        }
        return leaf;
    }
}
