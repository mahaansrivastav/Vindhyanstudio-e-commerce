// Copyright (c) 2026 Mahan Srivastava. All rights reserved.
// Licensed, not sold. Copying, modifying or sharing is not permitted without written permission. See LICENSE.
import { BrowserRouter, Link, Route, Routes } from 'react-router-dom'
import { Admin } from './admin/Admin'
import { Layout } from './components/Layout'
import { Empty, RequireAuth } from './components/ui'
import { Account } from './pages/Account'
import { Cart } from './pages/Cart'
import { Checkout } from './pages/Checkout'
import { Home } from './pages/Home'
import { Login } from './pages/Login'
import { OrderDetail } from './pages/OrderDetail'
import { Policy } from './pages/Policy'
import { Pay } from './pages/Pay'
import { Product } from './pages/Product'
import { Shop } from './pages/Shop'
import { Wishlist } from './pages/Wishlist'
import { AppStateProvider } from './state/AppState'
import { PaymentProvider } from './state/Payments'

export default function App() {
  return (
    <AppStateProvider>
      <PaymentProvider>
        <BrowserRouter>
          <Routes>
            <Route element={<Layout />}>
              <Route index element={<Home />} />
              <Route path="shop" element={<Shop />} />
              <Route path="p/:slug" element={<Product />} />
              <Route path="cart" element={<Cart />} />
              <Route path="wishlist" element={<Wishlist />} />
              <Route path="login" element={<Login />} />
              <Route path="policies/:slug" element={<Policy />} />
              <Route path="checkout" element={<RequireAuth><Checkout /></RequireAuth>} />
              <Route path="pay/:id" element={<RequireAuth><Pay /></RequireAuth>} />
              <Route path="account/orders/:id" element={<RequireAuth><OrderDetail /></RequireAuth>} />
              <Route path="account/*" element={<RequireAuth><Account /></RequireAuth>} />
              <Route path="admin/*" element={<RequireAuth admin><Admin /></RequireAuth>} />
              <Route path="*" element={<Empty title="Page not found"><Link to="/" className="underline">Go home</Link></Empty>} />
            </Route>
          </Routes>
        </BrowserRouter>
      </PaymentProvider>
    </AppStateProvider>
  )
}
