package com.coy.discover;

import java.util.List;

/** Public profile shape. Deliberately has no "likesYou" field. */
public record Profile(int id, String name, int age, String photo, String bio, String city, List<String> interests) {}
