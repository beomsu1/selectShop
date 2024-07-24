package com.sparta.myselectshop.service;

import com.sparta.myselectshop.dto.ProductMypriceRequestDto;
import com.sparta.myselectshop.dto.ProductRequestDto;
import com.sparta.myselectshop.dto.ProductResponseDto;
import com.sparta.myselectshop.entity.*;
import com.sparta.myselectshop.exception.ProductNotFoundException;
import com.sparta.myselectshop.naver.dto.ItemDto;
import com.sparta.myselectshop.repository.FolderRepository;
import com.sparta.myselectshop.repository.ProductFolderRepository;
import com.sparta.myselectshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    private final FolderRepository folderRepository;

    private final ProductFolderRepository productFolderRepository;

    private final MessageSource messageSource;

    public static final int MIN_MY_PRICE = 100; // 최소가격

    public ProductResponseDto createProduct(ProductRequestDto requestDto, User user) {

        Product product = productRepository.save(new Product(requestDto, user));
        return new ProductResponseDto(product);
    }

    @Transactional // Dirty Checking -> 트랜잭션 환경 필수
    public ProductResponseDto updateProduct(Long id, ProductMypriceRequestDto requestDto) {

        int myPrice = requestDto.getMyprice();

        if (myPrice < MIN_MY_PRICE) {

            throw new IllegalArgumentException(
                    messageSource.getMessage("below.min.my.price", new Integer[]{MIN_MY_PRICE}, "Wrong Price", Locale.getDefault())
            );
        }

        Product product = productRepository.findById(id).orElseThrow(
                () -> new ProductNotFoundException(
                        messageSource.getMessage("not.found.product", null, "Not Found Product", Locale.getDefault())));

        product.update(requestDto);

        return new ProductResponseDto(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDto> getProducts(User user, int page, int size, String sortBy, boolean isAsc) {

        Sort.Direction direction = isAsc ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        UserRoleEnum userRoleEnum = user.getRole();

        Page<Product> productList;

        if (userRoleEnum == UserRoleEnum.USER) {
            productList = productRepository.findAllByUser(user, pageable);
        } else {
            productList = productRepository.findAll(pageable);
        }

//        List<Product> productList = productRepository.findAllByUser(user);
//        List<ProductResponseDto> responseDtoList = new ArrayList<>();
//
//        for (Product product : productList) {
//            responseDtoList.add(new ProductResponseDto(product));
//        }

        return productList.map(ProductResponseDto::new);
    }

    @Transactional
    public void updateBySearch(Long id, ItemDto itemDto) {

        Product product = productRepository.findById(id).orElseThrow(() -> new NullPointerException("해당 상품은 존재하지 않습니다."));

        product.updateByItemDto(itemDto);
    }

    public void addFolder(Long productID, Long folderId, User user) {

        Product product = productRepository.findById(productID).orElseThrow(() -> new NullPointerException("해당 상품이 존재하지 않습니다."));
        Folder folder = folderRepository.findById(folderId).orElseThrow(() -> new NullPointerException("해당 폴더가 존재하지 않습니다."));

        if (!product.getUser().getId().equals(user.getId()) || !folder.getId().equals(user.getId())) {
            throw new IllegalArgumentException("회원님의 관심 상품이 아니거나, 회원님의 폴더가 아닙니다.");
        }

        // 폴더 중복 확인
        Optional<ProductFolder> overlapFolder = productFolderRepository.findByProductAndFolder(product, folder);

        if (overlapFolder.isPresent()) {
            throw new IllegalArgumentException("중복된 폴더입니다.");
        }

        // ProductFolder에 등록
        productFolderRepository.save(new ProductFolder(product, folder));

    }

    public Page<ProductResponseDto> getProductsInFolder(Long folderId, int page, int size, String sortBy, boolean isAsc, User user) {

        Sort.Direction direction = isAsc ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> ProductList = productRepository.findAllByUserAndProductFolderList_FolderId(folderId, user, pageable);

        Page<ProductResponseDto> responseDtoList = ProductList.map(ProductResponseDto::new);

        return responseDtoList;
    }
}
