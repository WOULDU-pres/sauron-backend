package com.sauron.common.utils;

import lombok.experimental.UtilityClass;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 컬렉션 관련 유틸리티 클래스
 * 다양한 컬렉션 처리 및 변환 기능을 제공합니다.
 */
@UtilityClass
public class CollectionUtils {
    
    /**
     * 컬렉션이 null이거나 비어있는지 확인
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }
    
    /**
     * 컬렉션이 null이 아니고 비어있지 않은지 확인
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }
    
    /**
     * 맵이 null이거나 비어있는지 확인
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }
    
    /**
     * 맵이 null이 아니고 비어있지 않은지 확인
     */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }
    
    /**
     * 배열이 null이거나 비어있는지 확인
     */
    public static boolean isEmpty(Object[] array) {
        return array == null || array.length == 0;
    }
    
    /**
     * 배열이 null이 아니고 비어있지 않은지 확인
     */
    public static boolean isNotEmpty(Object[] array) {
        return !isEmpty(array);
    }
    
    /**
     * 컬렉션의 크기 반환 (null 안전)
     */
    public static int size(Collection<?> collection) {
        return collection == null ? 0 : collection.size();
    }
    
    /**
     * 맵의 크기 반환 (null 안전)
     */
    public static int size(Map<?, ?> map) {
        return map == null ? 0 : map.size();
    }
    
    /**
     * 배열의 길이 반환 (null 안전)
     */
    public static int length(Object[] array) {
        return array == null ? 0 : array.length;
    }
    
    /**
     * 리스트에서 특정 인덱스의 요소를 안전하게 가져오기
     */
    public static <T> T safeGet(List<T> list, int index) {
        if (list == null || index < 0 || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }
    
    /**
     * 리스트에서 특정 인덱스의 요소를 안전하게 가져오기 (기본값 포함)
     */
    public static <T> T safeGet(List<T> list, int index, T defaultValue) {
        T value = safeGet(list, index);
        return value != null ? value : defaultValue;
    }
    
    /**
     * 리스트의 첫 번째 요소 가져오기
     */
    public static <T> T getFirst(List<T> list) {
        return safeGet(list, 0);
    }
    
    /**
     * 리스트의 마지막 요소 가져오기
     */
    public static <T> T getLast(List<T> list) {
        if (isEmpty(list)) {
            return null;
        }
        return list.get(list.size() - 1);
    }
    
    /**
     * 컬렉션을 다른 타입으로 변환
     */
    public static <T, R> List<R> transform(Collection<T> collection, Function<T, R> mapper) {
        if (isEmpty(collection)) {
            return new ArrayList<>();
        }
        return collection.stream()
                .map(mapper)
                .collect(Collectors.toList());
    }
    
    /**
     * 컬렉션 필터링
     */
    public static <T> List<T> filter(Collection<T> collection, Predicate<T> predicate) {
        if (isEmpty(collection)) {
            return new ArrayList<>();
        }
        return collection.stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }
    
    /**
     * 컬렉션에서 조건에 맞는 첫 번째 요소 찾기
     */
    public static <T> Optional<T> findFirst(Collection<T> collection, Predicate<T> predicate) {
        if (isEmpty(collection)) {
            return Optional.empty();
        }
        return collection.stream()
                .filter(predicate)
                .findFirst();
    }
    
    /**
     * 컬렉션에서 조건에 맞는 요소가 있는지 확인
     */
    public static <T> boolean anyMatch(Collection<T> collection, Predicate<T> predicate) {
        if (isEmpty(collection)) {
            return false;
        }
        return collection.stream().anyMatch(predicate);
    }
    
    /**
     * 컬렉션의 모든 요소가 조건에 맞는지 확인
     */
    public static <T> boolean allMatch(Collection<T> collection, Predicate<T> predicate) {
        if (isEmpty(collection)) {
            return true;
        }
        return collection.stream().allMatch(predicate);
    }
    
    /**
     * 컬렉션에서 null 요소 제거
     */
    public static <T> List<T> removeNulls(Collection<T> collection) {
        if (isEmpty(collection)) {
            return new ArrayList<>();
        }
        return collection.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    /**
     * 컬렉션에서 중복 제거
     */
    public static <T> List<T> removeDuplicates(Collection<T> collection) {
        if (isEmpty(collection)) {
            return new ArrayList<>();
        }
        return collection.stream()
                .distinct()
                .collect(Collectors.toList());
    }
    
    /**
     * 두 컬렉션의 교집합
     */
    public static <T> List<T> intersection(Collection<T> collection1, Collection<T> collection2) {
        if (isEmpty(collection1) || isEmpty(collection2)) {
            return new ArrayList<>();
        }
        Set<T> set2 = new HashSet<>(collection2);
        return collection1.stream()
                .filter(set2::contains)
                .distinct()
                .collect(Collectors.toList());
    }
    
    /**
     * 두 컬렉션의 합집합
     */
    public static <T> List<T> union(Collection<T> collection1, Collection<T> collection2) {
        Set<T> result = new LinkedHashSet<>();
        if (isNotEmpty(collection1)) {
            result.addAll(collection1);
        }
        if (isNotEmpty(collection2)) {
            result.addAll(collection2);
        }
        return new ArrayList<>(result);
    }
    
    /**
     * 첫 번째 컬렉션에서 두 번째 컬렉션의 요소들을 제외
     */
    public static <T> List<T> difference(Collection<T> collection1, Collection<T> collection2) {
        if (isEmpty(collection1)) {
            return new ArrayList<>();
        }
        if (isEmpty(collection2)) {
            return new ArrayList<>(collection1);
        }
        Set<T> set2 = new HashSet<>(collection2);
        return collection1.stream()
                .filter(item -> !set2.contains(item))
                .collect(Collectors.toList());
    }
    
    /**
     * 컬렉션을 특정 크기의 청크로 분할
     */
    public static <T> List<List<T>> partition(Collection<T> collection, int size) {
        if (isEmpty(collection) || size <= 0) {
            return new ArrayList<>();
        }
        
        List<T> list = new ArrayList<>(collection);
        List<List<T>> partitions = new ArrayList<>();
        
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        
        return partitions;
    }
    
    /**
     * 컬렉션을 키별로 그룹화
     */
    public static <T, K> Map<K, List<T>> groupBy(Collection<T> collection, Function<T, K> keyExtractor) {
        if (isEmpty(collection)) {
            return new HashMap<>();
        }
        return collection.stream()
                .collect(Collectors.groupingBy(keyExtractor));
    }
    
    /**
     * 컬렉션을 맵으로 변환
     */
    public static <T, K, V> Map<K, V> toMap(Collection<T> collection, 
                                           Function<T, K> keyMapper, 
                                           Function<T, V> valueMapper) {
        if (isEmpty(collection)) {
            return new HashMap<>();
        }
        return collection.stream()
                .collect(Collectors.toMap(keyMapper, valueMapper));
    }
    
    /**
     * 리스트를 역순으로 정렬
     */
    public static <T> List<T> reverse(List<T> list) {
        if (isEmpty(list)) {
            return new ArrayList<>();
        }
        List<T> reversed = new ArrayList<>(list);
        Collections.reverse(reversed);
        return reversed;
    }
    
    /**
     * 컬렉션을 랜덤하게 섞기
     */
    public static <T> List<T> shuffle(Collection<T> collection) {
        if (isEmpty(collection)) {
            return new ArrayList<>();
        }
        List<T> shuffled = new ArrayList<>(collection);
        Collections.shuffle(shuffled);
        return shuffled;
    }
    
    /**
     * 컬렉션에서 랜덤 요소 선택
     */
    public static <T> T randomElement(Collection<T> collection) {
        if (isEmpty(collection)) {
            return null;
        }
        List<T> list = new ArrayList<>(collection);
        return list.get(new Random().nextInt(list.size()));
    }
    
    /**
     * 컬렉션에서 지정된 개수만큼 랜덤 요소 선택
     */
    public static <T> List<T> randomElements(Collection<T> collection, int count) {
        if (isEmpty(collection) || count <= 0) {
            return new ArrayList<>();
        }
        
        List<T> list = new ArrayList<>(collection);
        Collections.shuffle(list);
        return list.subList(0, Math.min(count, list.size()));
    }
    
    /**
     * 배열을 리스트로 변환 (null 안전)
     */
    public static <T> List<T> arrayToList(T[] array) {
        if (array == null) {
            return new ArrayList<>();
        }
        return Arrays.asList(array);
    }
    
    /**
     * 가변 인수를 리스트로 변환
     */
    @SafeVarargs
    public static <T> List<T> listOf(T... elements) {
        if (elements == null) {
            return new ArrayList<>();
        }
        return Arrays.asList(elements);
    }
    
    /**
     * 가변 인수를 Set으로 변환
     */
    @SafeVarargs
    public static <T> Set<T> setOf(T... elements) {
        if (elements == null) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(elements));
    }
}