package com.sudoku.admin;

import jakarta.ws.rs.NameBinding;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Name-binding annotation restricting a resource or method to members of the
 * configured admin Cognito group. See {@link AdminAuthorizationFilter}.
 */
@NameBinding
@Target({TYPE, METHOD})
@Retention(RUNTIME)
public @interface AdminOnly {}
