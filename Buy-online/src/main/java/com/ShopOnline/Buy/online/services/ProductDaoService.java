package com.ShopOnline.Buy.online.services;

import com.ShopOnline.Buy.online.entities.Seller;
import com.ShopOnline.Buy.online.entities.User;
import com.ShopOnline.Buy.online.entities.category.Category;
import com.ShopOnline.Buy.online.entities.category.CategoryMetaDataField;
import com.ShopOnline.Buy.online.entities.product.Product;
import com.ShopOnline.Buy.online.entities.product.ProductVariation;
import com.ShopOnline.Buy.online.exceptions.BadRequestException;
import com.ShopOnline.Buy.online.exceptions.ResourceNotFoundException;
import com.ShopOnline.Buy.online.models.ProductModel;
import com.ShopOnline.Buy.online.models.ProductUpdateModel;
import com.ShopOnline.Buy.online.models.ProductUpdateVariationModel;
import com.ShopOnline.Buy.online.models.ProductVariationModel;
import com.ShopOnline.Buy.online.repos.CategoryMetadataFieldRepository;
import com.ShopOnline.Buy.online.repos.CategoryRepository;
import com.ShopOnline.Buy.online.repos.ProductRepository;
import com.ShopOnline.Buy.online.repos.ProductVariationRepository;
import com.ShopOnline.Buy.online.utils.HashMapCoverter;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.*;

@Service
public class ProductDaoService {

    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    EmailSenderService emailSenderService;
    @Autowired
    ProductVariationRepository productVariationRepository;
    @Autowired
    CategoryMetadataFieldRepository categoryMetadataFieldRepository;
    @Autowired
    UserDaoService userDaoService;

    public String addProduct(Long categoryId, Seller seller, ProductModel productModel) {
        Optional<Category> categoryOptional = categoryRepository.findById(categoryId);

        if(categoryOptional.isPresent()) {
            Category category = categoryOptional.get();
            if(category.getParentCategory() == null) {
                throw new BadRequestException("Product should be the leaf category, " + category.getName() + " is not a leaf category");
            }
            else {
                ModelMapper mapper = new ModelMapper();
                Product product = mapper.map(productModel, Product.class);

                String productName = productRepository.checkForUniqueness(product.getProductName(),product.getBrand(), category.getCategoryId(), seller.getUserId());

                if(product.getProductName().equals(productName)) {
                    throw new BadRequestException("Can't add the same product again, don't use the " + productName + " product to add as you had already saved it");
                }
                else {
                    product.setSeller(seller);
                    product.setCategory(category);
                    product.setCancellable(false);
                    product.setReturnable(false);
                    product.setActive(false);
                    product.setDeleted(false);

                    productRepository.save(product);

                    SimpleMailMessage mailMessage = new SimpleMailMessage();
                    mailMessage.setTo("adarsh.verma@tothenew.com");
                    mailMessage.setFrom("adarshv193@gmail.com");
                    mailMessage.setSubject("A new product has been added by the seller");
                    mailMessage.setText("A new product has been added by the seller, Please manage");

                    emailSenderService.sendEmail(mailMessage);

                    return "Product added successfully";
                }
            }
        }
        else {
            throw new ResourceNotFoundException("Invalid Category name, Category not found in the database with category id " + categoryId + " " );
        }
    }

    public String addProductVariation(Long productId, Seller seller, ProductVariationModel productVariationModel) {
        Optional<Product> productOptional = productRepository.findById(productId);

        if(productOptional.isPresent()) {
            Product product = productOptional.get();

            if(product.getActive() == false) {
                throw new BadRequestException("Can't add the product variations as the product is not active by the Admin ");
            }

            if(product.getDeleted() == true) {
                throw new BadRequestException("Can't add the product variation as the product is deleted ");
            }

            ModelMapper mapper = new ModelMapper();
            ProductVariation productVariation = mapper.map(productVariationModel, ProductVariation.class);

            Map<String, Object> productAttributes1 = productVariation.getProductAttributes();

            if(product.getSeller().getUserId().equals(seller.getUserId())) {

                List<String> allProductVariationAttributes = productVariationRepository.findAllProductVariationAttributes(productId);

                if(allProductVariationAttributes.size() != 0) {

                    HashMapCoverter hashMapCoverter = new HashMapCoverter();

                    for(String productAttributes : allProductVariationAttributes) {
                        Map<String, Object> map = hashMapCoverter.convertToEntityAttribute(productAttributes);
                        if(map.equals(productVariation.getProductAttributes())) {
                            throw new BadRequestException("Can't add the product variation with these product attributes , Please try some other varaition");
                        }
                    }

                    if(checkProductValidationModel(productId,productVariationModel)) {

                        if(productVariation.getQuantityAvailable() <= 0) {
                            throw new BadRequestException("Quantity should be greater than 0");
                        }
                        if(productVariation.getPrice() <= 0) {
                            throw new BadRequestException("Price should be greater than 0");
                        }

                        productVariation.setProduct(product);
                        productVariation.setActive(true);
                        productVariation.setDeleted(false);

                        productVariationRepository.save(productVariation);

                        return "Product Variant saved";
                    }
                    else {
                        return "Wrong category meta data field - values format ";
                    }
                }
                else {
                    if(checkProductValidationModel(productId,productVariationModel)) {

                        if(productVariation.getQuantityAvailable() <= 0) {
                            throw new BadRequestException("Quantity should be greater than 0");
                        }
                        if(productVariation.getPrice() <= 0) {
                            throw new BadRequestException("Price should be greater than 0");
                        }

                        productVariation.setProduct(product);
                        productVariation.setActive(true);
                        productVariation.setDeleted(false);

                        productVariationRepository.save(productVariation);

                        return "Product Variant saved";
                    }
                    else {
                        return "Wrong category meta data field - values format ";
                    }
                }
            }
            else {
                throw new BadRequestException("Product " + product.getProductName() + " is not associated with the logged in seller " + seller.getFirstName() + " ");
            }
        }
        else {
            throw new ResourceNotFoundException("Invalid product ID, product not found with ID " + productId + " ");
        }
    }

    public Boolean checkProductValidationModel(Long product_id, ProductVariationModel productVariationModel) {

        Product product = productRepository.findById(product_id).get();

        Map<String, String> productAttributes = productVariationModel.getProductAttributes();

        Set<String> modelKeySets = productAttributes.keySet();

        Integer modelMetadatLengthField = modelKeySets.size();


        if(modelMetadatLengthField <= 0) {
            throw new BadRequestException("There should atleast one metadata field values, and all the product variations should have the same format");
        }


        List<Long> categoryMetaDataFieldsIDs = productVariationRepository.checkDbMetadataLengthField(product.getCategory().getCategoryId());

        if(categoryMetaDataFieldsIDs.size() != modelMetadatLengthField) {
            throw new BadRequestException("The product variation category metadata field values does not match the structure");
        }

        Set<String> categoryMetaDataFieldListName = new HashSet<>();


        categoryMetaDataFieldsIDs.forEach(id -> {
            String name = categoryMetadataFieldRepository.findById(id).get().getName();
            categoryMetaDataFieldListName.add(name);
        });

        for(String field : modelKeySets) {
            Boolean checker = false;
            for(String dbField : categoryMetaDataFieldListName) {
                if(field.equals(dbField)) {
                 checker = true;
                }
            }
            if(checker == false) {
                throw new BadRequestException("Wrong metadata field name is inserted with name " + field + " ");
            }
        }

        return true;
    }

    public String updateProduct(Long productId, ProductUpdateModel productModel, Seller seller) {
        Optional<Product> productOptional = productRepository.findById(productId);
        if(productOptional.isPresent()) {
            Product product = productOptional.get();

            if(!seller.getUserId().equals(product.getSeller().getUserId())) {
                throw new BadRequestException("Can't update this product as this product does not get listed by you");
            }

            if(product.getActive() == false) {
                throw new BadRequestException("Can't update the " + product.getProductName() + "as the product is not activated by the Admin");
            }

            if(product.getDeleted() == true) {
                throw new BadRequestException("Can't update the " + product.getProductName() + "as the product is deleted");
            }

            if(productModel.getProductName() != null) {
                System.out.println("called");
                String productName = productModel.getProductName();
                String checkProductName = productRepository.checkForUniqueness(productName,product.getBrand(),product.getCategory().getCategoryId(),seller.getUserId());


                if(productName.equals(checkProductName)) {
                    throw new BadRequestException("As there is another product listed by you with name " + checkProductName + " can't update, Please try some other name");
                }

                product.setProductName(productModel.getProductName());
            }

            if(productModel.getProductDescription() != null) {
                product.setProductDescription(productModel.getProductDescription());
            }

            if(productModel.getCancellable() != null) {
                product.setCancellable(productModel.getCancellable());
            }

            if(productModel.getReturnable() != null) {
                product.setReturnable(productModel.getReturnable());
            }

            productRepository.save(product);

            return "Product updated successfully";
        }
        else {
            throw new BadRequestException("Invalid product Id, Product not found with the id " + productId + " ");
        }
    }

    public String updateProductVariaiton(Long productVariationId, ProductUpdateVariationModel productVariationModel, Seller seller) {
        Optional<ProductVariation> productVariationOptional = productVariationRepository.findById(productVariationId);
        if(productVariationOptional.isPresent()) {
            ProductVariation productVariation = productVariationOptional.get();

            if(!productVariation.getProduct().getSeller().getUserId().equals(seller.getUserId())) {
                throw new BadRequestException("Can't update this product variation as this product variation does not get listed by you");
            }

            if(productVariation.getDeleted() == true) {
                throw new BadRequestException("Can't update this product variation as this product variation is deleted");
            }

            if(productVariation.getActive() == false) {
                throw new BadRequestException("Can't update the " + productVariation.getVariantName() + "as the product is not activated by the Admin");
            }

            if(productVariationModel.getVariantName() != null) {
                List<ProductVariation> productVariationList = productVariationRepository.checkForProductvariationWithNameAndProductId(productVariationModel.getVariantName(), productVariation.getProduct().getProductId());
                if(productVariationList.size() != 0) {
                    throw new BadRequestException("Can't update the product variation as there exist a product variation with variation name " + productVariationModel.getVariantName() + " ");
                }

                productVariation.setVariantName(productVariationModel.getVariantName());
            }

            if(productVariationModel.getProductAttributes().size() > 0) {
                Map<String, Object> productAttributesClient = productVariationModel.getProductAttributes();

                List<String> allProductVariationAttributes = productVariationRepository.findAllProductVariationAttributes(productVariation.getProduct().getProductId());

                HashMapCoverter hashMapCoverter = new HashMapCoverter();

                for(String productAttributes : allProductVariationAttributes) {
                    Map<String, Object> map = hashMapCoverter.convertToEntityAttribute(productAttributes);
                    if(map.equals(productVariationModel.getProductAttributes())) {
                        throw new BadRequestException("Can't update the product variation with these product attributes , Please try some other varaition");
                    }
                }

                if(checkProductValidationModel(productVariation.getProduct().getProductId(),productVariationModel)) {
                    productVariation.setProductAttributes(productVariationModel.getProductAttributes());
                }
            }

            if(productVariationModel.getQuantityAvailable() != null) {
                productVariation.setQuantityAvailable(productVariationModel.getQuantityAvailable());
            }

            if(productVariationModel.getPrice() != null) {
                productVariation.setPrice(productVariationModel.getPrice());
            }

            productVariationRepository.save(productVariation);

            return "Product variation gets updated";
        }
        else {
            throw new ResourceNotFoundException("Invalid product variation id, No record found with the product variation id " + productVariationId + " ");
        }
    }

    public Boolean checkProductValidationModel(Long productId, ProductUpdateVariationModel productUpdateVariationModel) {
        Product product = productRepository.findById(productId).get();

        Map<String, Object> productAttributes = productUpdateVariationModel.getProductAttributes();
        Set<String> modelKeySet = productAttributes.keySet();

        if (modelKeySet.size() <= 0) {
            throw new BadRequestException("There should atleast one metadata field values, and all the product variations should have the same format");
        }

        List<Long> categoryMetaDataFieldsIDs = productVariationRepository.checkDbMetadataLengthField(product.getCategory().getCategoryId());

        if(modelKeySet.size() != categoryMetaDataFieldsIDs.size()) {
            throw new BadRequestException("The product variation category metadata field values does not match the structure");
        }

        Set<String> categoryMetaDataFieldListName = new HashSet<>();

        categoryMetaDataFieldsIDs.forEach(id -> {
            categoryMetaDataFieldListName.add(categoryMetadataFieldRepository.findById(id).get().getName());
        });

        for(String field : modelKeySet) {
            Boolean checker = false;
            for(String dbField : categoryMetaDataFieldListName) {
                if(field.equals(dbField)) {
                    checker = true;
                }
            }
            if(checker == false) {
                throw new BadRequestException("Wrong metadata field name is inserted with name " + field + " ");
            }
        }

        return true;
    }

    @Transactional
    public String activateProduct(Long productId) {
        Optional<Product> productOptional = productRepository.findById(productId);
        if(productOptional.isPresent()) {
            Product product = productOptional.get();
            if(product.getActive() == false) {
                System.out.println("Called");
                System.out.println(product.getDeleted());
                if(product.getDeleted() == false) {
                    product.setActive(true);

                    productRepository.save(product);

                    SimpleMailMessage mailMessage = new SimpleMailMessage();
                    mailMessage.setTo(product.getSeller().getEmail());
                    mailMessage.setFrom("adarshv193@gmail.com");
                    mailMessage.setSubject("Product Activated");
                    mailMessage.setText("Your product " + product.getProductName() + " has been activated by our team ");
                    emailSenderService.sendEmail(mailMessage);

                    return "Product " + product.getProductName()  + " with id " + productId + " is activated";
                }
                else {
                    return "Can't activate the product " + product.getProductName() + " as it is deleted";
                }
            }
            else {
                return "The product " + product.getProductName() + " is already activated";
            }
        }
        else {
            throw new ResourceNotFoundException("Invalid product ID, There is not product listed with product ID " + productId + " ");
        }
    }

    @Transactional
    public String deActivatedProduct(Long productId) {
        Optional<Product> productOptional = productRepository.findById(productId);
        if(productOptional.isPresent()) {
            Product product = productOptional.get();
            if(product.getActive() == true) {

                if(product.getDeleted() == false) {
                    product.setActive(false);

                    productRepository.save(product);

                    SimpleMailMessage mailMessage = new SimpleMailMessage();
                    mailMessage.setTo(product.getSeller().getEmail());
                    mailMessage.setFrom("adarshv193@gmail.com");
                    mailMessage.setSubject("Product Deactivated");
                    mailMessage.setText("Your product " + product.getProductName() + " has been deactivated by our team ");
                    emailSenderService.sendEmail(mailMessage);

                    return "Product with " + productId + " is deactivated";
                }
                else {
                    return "Can't deactivate product " + product.getProductName() + " as it is deleted";
                }
            }
            else {
                return "Product " + product.getProductName() + " is already deactivated";
            }
        }
        else {
            throw new ResourceNotFoundException("Invalid product ID, No product found with the product ID " + productId + " ");
        }
    }

    @Transactional
    public Product findProductForSeller(Long productId) {
        Optional<Product> productOptional = productRepository.findById(productId);
        if(productOptional.isPresent()) {
            Product product = productOptional.get();

            Seller seller = (Seller) userDaoService.getLoggedInSeller();
            if(!seller.getUserId().equals(product.getSeller().getUserId())) {
                throw new BadRequestException("Invalid seller ID, The user which is trying to access the product is not the creator of the product");
            }

            if(product.getActive() && !product.getDeleted()) {
                return product;
            }
            else {
                throw new BadRequestException("Product is unavailable at the moment either it is deleted or not in active state");
            }
        }
        else {
            throw new ResourceNotFoundException("Invalid Product id, no product found with the id " + productId + " ");
        }
    }

    @Transactional
    public Product customerViewProduct(Long productId) {
        Optional<Product> productOptional = productRepository.findById(productId);
        if(productOptional.isPresent()) {
            Product product = productOptional.get();

            if(product.getActive() && !product.getDeleted()) {
                if(product.getProductVariationSet().size() == 0) {
                    throw new BadRequestException("As the selected product does not have any valid product variation, please view some other product");
                }

                return product;
            }
            else {
                throw new BadRequestException("Product is unavailable at the moment either it is deleted or not in active state");
            }
        }
        else {
            throw new ResourceNotFoundException("Invalid Product id, no product found with the id " + productId + " ");
        }
    }

    @Transactional
    public ProductVariation findProductVariationForSeller(Long productVariaitonId) {
        Optional<ProductVariation> productVariationOptional = productVariationRepository.findById(productVariaitonId);
        if(productVariationOptional.isPresent()) {
            ProductVariation productVariation = productVariationOptional.get();

            Seller seller = (Seller) userDaoService.getLoggedInSeller();

            if(!seller.getUserId().equals(productVariation.getProduct().getSeller().getUserId())) {
                throw new BadRequestException("Invalid seller ID, The user which is trying to access the product variation is not the creator of the product variation");
            }

            Product product = productVariation.getProduct();

            if(product.getActive() && !product.getDeleted()) {
                if(productVariation.getActive() && !productVariation.getDeleted()) {
                    return productVariation;
                }
                else {
                    throw new BadRequestException("Product variation is unavailable at the moment either it is deleted or not in active state");
                }
            }
            else {
                throw new BadRequestException("Product is unavailable at the moment either it is deleted or not in active state");
            }
        }
        else {
            throw new ResourceNotFoundException("Invalid Product varaition id, no product variation found with the id " + productVariaitonId + " ");
        }
    }

    public List<Product> findSellerWiseAllProducts(Long sellerId) {
        return productRepository.findSellerAssociatedProducts(sellerId);
    }

    @Transactional
    public String deleteProduct(long productId, Long sellerId) {
        Optional<Product> productOptional = productRepository.findById(productId);
        if(productOptional.isPresent()) {
            Product product = productOptional.get();

            if(!product.getActive()) {
                throw new BadRequestException("Can't delete the product as it is not activated");
            }

            if(product.getDeleted()) {
                return "Product is already deleted";
            }

            if(!product.getSeller().getUserId().equals(sellerId)) {
                throw new BadRequestException("Invalid seller ID, The user which is trying to delete the product is not the creator of the product");
            }

            List<ProductVariation> allProductVariation = productVariationRepository.findAllProductVariationWithProductId(product.getProductId());

            allProductVariation.forEach(productVariation -> {
                productVariation.setActive(false);
                productVariationRepository.save(productVariation);
            });

            product.setDeleted(true);

            productRepository.save(product);

            return "Product deleted successfully";
        }
        else {
            throw new ResourceNotFoundException("Invalid Product id, no product found with the id " + productId + " ");
        }
    }

    @Transactional
    public List<Product> customerFindAllProductsCategoryWise(Long categoryId) {
        Optional<Category> categoryOptional = categoryRepository.findById(categoryId);
        if(categoryOptional.isPresent()) {
            Category category = categoryOptional.get();

            List<Product> products = new ArrayList<>();

            if(category.getParentCategory() == null) {
                List<Long> allChildCategoriesId = categoryRepository.findAllChildCategoriesId(category.getCategoryId());
                for(Long id :  allChildCategoriesId) {
                    List<Product> productByCategory = productRepository.findProductByCategory(id);

                    for(Product product : productByCategory) {
                        products.add(product);
                    }
                }

                return products;
            }
            else {
                products = productRepository.findProductByCategory(category.getCategoryId());

                return products;
            }
        }
        else {
            throw new ResourceNotFoundException("Invalid category Id, no category found wiht the id " + categoryId);
        }
    }

    @Transactional
    public List<Product> customerGetAllSimilarProduct(Long productId) {
        Optional<Product> productOptional = productRepository.findById(productId);
        if(productOptional.isPresent()) {
            Product product = productOptional.get();

            return productRepository.findProductByCategory(product.getCategory().getCategoryId());
        }
        else {
            throw new ResourceNotFoundException("Invalid Product id, no product found with the id " + productId + " ");
        }
    }

    @Transactional
    public List<Product> adminFindAllProducts() {
       return productRepository.findAllProducts();
    }
}
