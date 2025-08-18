package com.ex.final22c.data.cart;

import java.util.ArrayList;
import java.util.List;

import com.ex.final22c.service.cart.CartService;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CartSelectionForm {
        private List<Item> items = new ArrayList<>();

        @Getter @Setter
        public static class Item {
            private Long cartDetailId;
            private int quantity;
        }

        public List<CartService.SelectionItem> toSelectionItems() {
            List<CartService.SelectionItem> list = new ArrayList<>();
            if (items != null) {
                for (Item i : items) {
                    list.add(new CartService.SelectionItem(i.getCartDetailId(), i.getQuantity()));
                }
            }
            return list;
        }
}
