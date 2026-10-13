package ru.t1.authomatization.ui.test;

import com.codeborne.selenide.Condition;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Link;
import io.qameta.allure.Owner;
import org.junit.jupiter.api.*;
import ru.t1.authomatization.api.pageobject.GoodsApi;
import ru.t1.authomatization.ui.base.BaseUiTest;
import ru.t1.authomatization.ui.pageobject.*;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("ui")
@Epic("UI-тесты SmartShop на базе HomeWork6")
@Feature("Администрирование")
@Owner("QA-команда SmartShop")
@Link(name = "Swagger API", url = "http://127.0.0.1:8080/swagger-ui/index.html")
public class Homework6Tests extends BaseUiTest {

    private static String productNameOne = "Homework6Tests product";
    private static double productPriceOne = 30;
    private static long createdProductId;

    @BeforeAll
    public static void setUpBeforeAll() {
        GoodsApi goodsApi = new GoodsApi();
        createdProductId = goodsApi.createProductAndReturnId(productNameOne, productPriceOne);
    }

    @AfterAll
    public static void tearDownAfterAll() {
        GoodsApi goodsApi = new GoodsApi();
        goodsApi.deleteProduct(createdProductId);
    }

    @Test
    @DisplayName("1.1. Добавить товар через админку, выйти на витрину и проверить, что товар отображается.")
    public void test_1_1_add_product_and_check_vitrine() throws InterruptedException {
        String productName = "My Product " + System.currentTimeMillis() ;
        double productPrice = 500.1;
        AdminPage adminPage = new AdminPage().openPage();
        if (isOnLoginPage()) {
            adminPage = new LoginPage()
                    .loginAs(ru.t1.authomatization.api.config.ApiConfig.getUsername(),
                            ru.t1.authomatization.api.config.ApiConfig.getPassword());
        }
        adminPage.checkPageDisplayed();
        checkUrl("/admin");

        // Запоминаем число строк ДО добавления.
        int rowsBefore = adminPage.getTableRowCount();

        // Действие: создаём товар через UI админки (поля + «Создать»).
        String newProductName = uniqueName(productName);
        adminPage.addProduct(newProductName, productPrice);

        // Проверка 1: количество строк в таблице увеличилось на 1.
        // (JS перерисовывает таблицу после ответа сервера.)
        adminPage.checkTableRowCount(rowsBefore + 1);

        // Проверка 2: находим созданный товар в таблице по названию
        // и проверяем, что его id действительно появился.
        long newProductId = adminPage.findProductIdByName(newProductName);
        adminPage.checkProductInTable(newProductId);
        assertThat(adminPage.getProductPrice(newProductId)).isEqualTo(productPrice);

        MainPage mainPage = new MainPage().openPage();
        mainPage.checkPageDisplayed();
        ProductCard newProduct = mainPage.productCard(newProductId);
        newProduct.checkVisible();
        // Регистрируем id для очистки в @AfterEach (API-подчистка).
        createdProductIds.add(newProductId);
        Thread.sleep(20000);
    }

    @Test
    @DisplayName("1.2. Добавить товар в корзину и проверить, что он отображается.")
    public void test_1_2_add_to_cart_and_check() {
        MainPage mainPage = new MainPage();
        mainPage.checkPageDisplayed();

        // На старте счётчик = 0.
        assertThat(mainPage.getCartCount()).as("счётчик корзины до добавления").isEqualTo(0);

        mainPage.productCard(createdProductId).addToCart(1);
        mainPage.headers().cartCountElement().shouldHave(Condition.exactText("1"));
    }

    @Test
    @DisplayName("1.3. Попытаться войти в админку с неверным логином и паролем.")
    public void test_1_3_wrong_login() {
        LoginPage loginPage = new LoginPage().openPage();
        loginPage.checkPageDisplayed();
        loginPage.loginExpectingFailure("negativetest",
                            "negativetest");
    }

    @Test
    @DisplayName("1.4. Проверить сохранение товаров в корзине после обновления страницы.")
    public void test_1_4_cart_persistence() {
        MainPage mainPage = new MainPage();
        mainPage.checkPageDisplayed();

        // На старте счётчик = 0.
        assertThat(mainPage.getCartCount()).as("счётчик корзины до добавления").isEqualTo(0);

        mainPage.productCard(createdProductId).addToCart(1);
        mainPage.headers().cartCountElement().shouldHave(Condition.exactText("1"));

        mainPage.refreshPage();

        assertThat(mainPage.getCartCount()).as("счётчик корзины после обновления").isEqualTo(0);
    }

    @Test
    @DisplayName("3.1. Добавить три единицы товара в корзину и оплатить их (общая стоимость <= 300 руб).")
    void testBuyThreeItemsWithinLimit() {
        MainPage mainPage = new MainPage();
        mainPage.checkPageDisplayed();

        // На старте счётчик = 0.
        assertThat(mainPage.getCartCount()).as("счётчик корзины до добавления").isEqualTo(0);

        mainPage.productCard(createdProductId).addToCart(3);
        mainPage.headers().cartCountElement().shouldHave(Condition.exactText("3"));

        CartModal cartModal = mainPage.openCart();
        cartModal.checkVisible();
        cartModal.clickCheckout();
        mainPage.checkPageDisplayed();
    }

    @Test
    @DisplayName("3.2. Добавить в корзину несколько разных товаров и проверить, что общая цена считается корректно.")
    void testCartTotalCalculation() {
        double productPrice = 50;
        GoodsApi goodsApi = new GoodsApi();
        long productId = goodsApi.createProductAndReturnId("Autotest_product_add", productPrice);

        MainPage mainPage = new MainPage().openPage();
        mainPage.checkPageDisplayed();

        // На старте счётчик = 0.
        assertThat(mainPage.getCartCount()).as("счётчик корзины до добавления").isEqualTo(0);

        mainPage.productCard(createdProductId).addToCart(1);
        mainPage.productCard(productId).addToCart(1);
        mainPage.headers().cartCountElement().shouldHave(Condition.exactText("2"));

        CartModal cartModal = mainPage.openCart();
        cartModal.checkVisible();
        cartModal.checkTotalEqualsSumOfItems();
        createdProductIds.add(productId);
    }

    @Test
    @DisplayName("3.3. Войти в админку и добавить товар. Проверить уведомление после добавления товара.")
    void testAdminAddProductWithApiData() {
        String productName = "My Product " + System.currentTimeMillis();
        double productPrice = 50;
        AdminPage adminPage = new AdminPage().openPage();
        if (isOnLoginPage()) {
            adminPage = new LoginPage()
                    .loginAs(ru.t1.authomatization.api.config.ApiConfig.getUsername(),
                            ru.t1.authomatization.api.config.ApiConfig.getPassword());
        }
        adminPage.checkPageDisplayed();
        checkUrl("/admin");

        // Запоминаем число строк ДО добавления.
        int rowsBefore = adminPage.getTableRowCount();

        // Действие: создаём товар через UI админки (поля + «Создать»).
        String newProductName = uniqueName(productName);
        adminPage.addProduct(newProductName, productPrice);
        adminPage.checkTableRowCount(rowsBefore + 1);

        // Проверка 2: находим созданный товар в таблице по названию
        // и проверяем, что его id действительно появился.
        long newProductId = adminPage.findProductIdByName(newProductName);
        createdProductIds.add(newProductId);
    }

    @Test
    @DisplayName("3.4. Войти в админку и отредактировать товар. Выйти на список товаров и проверить, что изменения применились.")
    void testAdminEditProduct() {
        String productName = "My Product " + System.currentTimeMillis();
        double productPrice = 50;
        AdminPage adminPage = new AdminPage().openPage();
        if (isOnLoginPage()) {
            adminPage = new LoginPage()
                    .loginAs(ru.t1.authomatization.api.config.ApiConfig.getUsername(),
                            ru.t1.authomatization.api.config.ApiConfig.getPassword());
        }
        adminPage.checkPageDisplayed();
        adminPage.updateProductName(createdProductId,productName);

        MainPage mainPage = new MainPage().openPage();
        mainPage.checkPageDisplayed();

        assertThat(mainPage.productCard(createdProductId).getDataName()).isEqualTo(productName);


    }
}
